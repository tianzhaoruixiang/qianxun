/**
 * Phase 2：对话成功后异步固化到 Mem0（双车道）。
 * 请求线程只入队，失败不影响主对话。
 */

import { createHash } from "node:crypto";
import { loadMemoryConfig } from "./config.js";
import { createMemoryClient } from "./client.js";
import { buildLaneFilters, resolveMemoryScope } from "./scope.js";
import { redactSecrets } from "./redact.js";
import { defaultMemoryQueue } from "./queue.js";

/**
 * @param {object} args
 * @param {string} args.userId
 * @param {string} args.prompt
 * @param {string} [args.assistantText]
 * @param {object} [args.body]
 * @param {boolean} [args.officer]
 * @param {boolean} [args.ok] 本轮是否成功（非 error result）
 * @param {string} [args.sessionId]
 * @param {object} [args.config]
 * @param {ReturnType<typeof createMemoryClient>} [args.client]
 * @param {ReturnType<typeof import("./queue.js").createMemoryQueue>} [args.queue]
 * @returns {Promise<{ enqueued: boolean, jobs: number, reason?: string }>}
 */
export async function memoryPersist({
  userId,
  prompt,
  assistantText = "",
  body = {},
  officer = false,
  ok = true,
  sessionId = "",
  config,
  client,
  queue = defaultMemoryQueue,
} = {}) {
  const cfg = config || loadMemoryConfig(body);
  if (!cfg.enabled || !cfg.writeEnabled) {
    return { enqueued: false, jobs: 0, reason: "disabled" };
  }
  if (!ok) {
    return { enqueued: false, jobs: 0, reason: "turn_failed" };
  }

  const userMsg = String(prompt || "").trim();
  const asstMsg = String(assistantText || "").trim();
  if (!userMsg) {
    return { enqueued: false, jobs: 0, reason: "empty_prompt" };
  }
  // 纯斜杠命令通常无长期事实价值
  if (userMsg.startsWith("/")) {
    return { enqueued: false, jobs: 0, reason: "slash" };
  }
  if (!asstMsg) {
    return { enqueued: false, jobs: 0, reason: "empty_assistant" };
  }

  const scope = resolveMemoryScope({
    userId,
    body,
    officer,
    appId: cfg.appId,
    prefsAgentId: cfg.prefsAgentId,
  });
  if (!scope.userId) {
    return { enqueued: false, jobs: 0, reason: "no_user" };
  }

  const maxChars = cfg.writeMaxChars || 4_000;
  const messages = buildPersistMessages(userMsg, asstMsg, maxChars);
  if (!messages.length) {
    return { enqueued: false, jobs: 0, reason: "empty_after_redact" };
  }

  const mem = client || createMemoryClient(cfg);
  if (!mem.enabled || typeof mem.add !== "function") {
    return { enqueued: false, jobs: 0, reason: "client_disabled" };
  }

  const runId = String(sessionId || body.sessionId || body.workspaceSessionId || "").trim() || undefined;
  const turnHash = hashTurn(scope.userId, runId || "", userMsg, asstMsg);
  const lanes = [];
  if (scope.includePrefsLane) {
    lanes.push({ lane: "prefs", filters: buildLaneFilters(scope, "prefs", { includeAppId: cfg.includeAppId }) });
  }
  if (scope.includeAgentLane) {
    lanes.push({ lane: "agent", filters: buildLaneFilters(scope, "agent", { includeAppId: cfg.includeAppId }) });
  }

  let jobs = 0;
  for (const { lane, filters } of lanes) {
    if (!filters) {
      continue;
    }
    const jobId = `${turnHash}:${lane}`;
    const metadata = {
      app_id: scope.appId,
      lane,
      turn_hash: turnHash,
      source: "qianxun.chat",
    };
    const result = queue.enqueue({
      id: jobId,
      label: `lane=${lane} user=${scope.userId}`,
      maxAttempts: cfg.writeMaxAttempts,
      run: async () => {
        await mem.add(messages, {
          user_id: filters.user_id,
          agent_id: filters.agent_id,
          run_id: runId,
          metadata,
        });
      },
    });
    if (result.enqueued) {
      jobs += 1;
    }
  }

  return {
    enqueued: jobs > 0,
    jobs,
    reason: jobs > 0 ? "ok" : "duplicate_or_no_lane",
  };
}

/**
 * @param {string} userMsg
 * @param {string} asstMsg
 * @param {number} maxChars
 */
export function buildPersistMessages(userMsg, asstMsg, maxChars = 4_000) {
  const budget = Math.max(200, maxChars | 0);
  const half = Math.floor(budget / 2);
  const user = clip(redactSecrets(userMsg), half);
  const assistant = clip(redactSecrets(asstMsg), budget - user.length);
  const out = [];
  if (user) {
    out.push({ role: "user", content: user });
  }
  if (assistant) {
    out.push({ role: "assistant", content: assistant });
  }
  return out;
}

function clip(text, max) {
  const s = String(text || "").trim();
  if (!s) {
    return "";
  }
  if (s.length <= max) {
    return s;
  }
  return s.slice(0, Math.max(0, max - 1)) + "…";
}

function hashTurn(userId, sessionId, prompt, assistant) {
  return createHash("sha256")
    .update(`${userId}\0${sessionId}\0${prompt}\0${assistant}`)
    .digest("hex")
    .slice(0, 24);
}
