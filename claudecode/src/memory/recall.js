import { loadMemoryConfig } from "./config.js";
import { createMemoryClient } from "./client.js";
import { formatMemoryAppend, mergeMemoryHits } from "./format.js";
import { buildLaneFilters, resolveMemoryScope } from "./scope.js";

/**
 * Phase 1：双车道召回并格式化为 system append。
 * 超时/失败降级为空字符串，不阻断主对话。
 *
 * @param {object} args
 * @param {string} args.userId
 * @param {string} args.prompt
 * @param {object} [args.body]
 * @param {boolean} [args.officer]
 * @param {object} [args.config] 已加载配置；省略则从 env+body 读取
 * @param {ReturnType<typeof createMemoryClient>} [args.client]
 * @returns {Promise<{ append: string, hits: object[], degraded: boolean, reason?: string }>}
 */
export async function memoryRecall({
  userId,
  prompt,
  body = {},
  officer = false,
  config,
  client,
} = {}) {
  const cfg = config || loadMemoryConfig(body);
  if (!cfg.enabled) {
    return { append: "", hits: [], degraded: false, reason: "disabled" };
  }

  const q = String(prompt || "").trim();
  if (!q) {
    return { append: "", hits: [], degraded: false, reason: "empty_prompt" };
  }

  const scope = resolveMemoryScope({
    userId,
    body,
    officer,
    appId: cfg.appId,
    prefsAgentId: cfg.prefsAgentId,
  });
  if (!scope.userId) {
    return { append: "", hits: [], degraded: false, reason: "no_user" };
  }

  const mem = client || createMemoryClient(cfg);
  const perLane = Math.max(1, Math.ceil(cfg.topK / 2));

  try {
    const tasks = [];
    if (scope.includePrefsLane) {
      const filters = buildLaneFilters(scope, "prefs", { includeAppId: cfg.includeAppId });
      tasks.push(mem.search(q, { filters, topK: perLane }).then((hits) => ({ lane: "prefs", hits })));
    }
    if (scope.includeAgentLane) {
      const filters = buildLaneFilters(scope, "agent", { includeAppId: cfg.includeAppId });
      tasks.push(mem.search(q, { filters, topK: perLane }).then((hits) => ({ lane: "agent", hits })));
    }

    if (!tasks.length) {
      return { append: "", hits: [], degraded: false, reason: "no_lanes" };
    }

    const settled = await Promise.allSettled(tasks);
    let prefsHits = [];
    let agentHits = [];
    let anyRejected = false;
    for (const r of settled) {
      if (r.status !== "fulfilled") {
        anyRejected = true;
        continue;
      }
      if (r.value.lane === "prefs") {
        prefsHits = r.value.hits;
      } else if (r.value.lane === "agent") {
        agentHits = r.value.hits;
      }
    }

    const hits = mergeMemoryHits(prefsHits, agentHits, { topK: cfg.topK });
    const append = formatMemoryAppend(hits, { maxChars: cfg.maxChars });
    return {
      append,
      hits,
      degraded: anyRejected,
      reason: anyRejected ? "partial_failure" : (hits.length ? "ok" : "empty"),
    };
  } catch (err) {
    console.warn(`[memory] recall degraded: ${err?.message || err}`);
    return { append: "", hits: [], degraded: true, reason: "error" };
  }
}
