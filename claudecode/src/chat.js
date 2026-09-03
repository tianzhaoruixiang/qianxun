import fs from "node:fs/promises";
import path from "node:path";
import { query } from "@anthropic-ai/claude-agent-sdk";
import {
  claudeHome,
  claudeMd,
  profileHome,
  sessionFile,
  workspace,
  legacySessionCwd,
  sessionCwd,
} from "./paths.js";
import { allowedClaudeTools, DEFAULT_ENABLED, disallowedClaudeTools, disallowedClaudeToolsFromAllowlist, expandEnabledToolsets, lowerSet } from "./toolsets.js";
import { createProfile, ensureDiscoverableSkillsLayout, listSkills, migrateLegacyClaudeHome, readToolsets } from "./store.js";
import {
  buildSdkMcpConfig,
  listMcpServers,
  listPlugins,
  syncMcpJsonFile,
  syncPluginsManifest,
} from "./mcp.js";
import { sanitizeOwnerId } from "./paths.js";
import { buildOfficerMcp } from "./officerMcp.js";
import {
  applyOfficerLeanTools,
  officerAllowedTools,
  officerBuiltinDelegationTools,
  officerHint,
  withOfficerMcpTools,
} from "./officerPolicy.js";
import { buildSystemAppend, clipSoul } from "./systemAppend.js";
import { applySeedHistory, isSlashPrompt } from "./promptSeed.js";
import { SDK_SETTING_SOURCES, buildSystemPrompt, skillToolEnabled } from "./sdkOptions.js";
import { emitContextUsageSnapshot } from "./contextUsage.js";
import { memoryRecall, memoryPersist, accumulateAssistantText } from "./memory/index.js";

async function readProfileSoul(home) {
  try {
    return clipSoul(await fs.readFile(claudeMd(home), "utf8"));
  } catch {
    return "";
  }
}

const PROXY_ENV_KEYS = [
  "HTTP_PROXY",
  "HTTPS_PROXY",
  "ALL_PROXY",
  "http_proxy",
  "https_proxy",
  "all_proxy",
];

/**
 * 空字符串 HTTP_PROXY= 仍会被 Bun/undici 当成「启用代理」，内网会空等很久再 ConnectionRefused，
 * 且请求到不了 LiteLLM。必须 delete，不能赋值为 ""。
 */
export function stripProxyEnv(env) {
  if (!env) {
    return env;
  }
  for (const key of PROXY_ENV_KEYS) {
    delete env[key];
  }
  env.NO_PROXY = "*";
  env.no_proxy = "*";
  return env;
}

const DEFAULT_ANTHROPIC_BASE = "http://litellm:4000";

/**
 * 容器内 127.0.0.1 / localhost 不是宿主机。3456 常见于本机 Claude Code Router，
 * sidecar 里连它会空等后 Operation aborted，LiteLLM 无日志。
 */
export function resolveAnthropicBaseUrl(raw) {
  const fallback = DEFAULT_ANTHROPIC_BASE;
  const value = String(raw || "").trim();
  if (!value) {
    return fallback;
  }
  let url;
  try {
    url = new URL(value);
  } catch {
    console.warn(`[claude-code] 非法 ANTHROPIC_BASE_URL=${value}，改用 ${fallback}`);
    return fallback;
  }
  const host = (url.hostname || "").toLowerCase();
  if (host === "127.0.0.1" || host === "localhost" || host === "::1" || host === "0.0.0.0") {
    console.warn(`[claude-code] ANTHROPIC_BASE_URL=${value} 在容器内不可用，改用 ${fallback}`);
    return fallback;
  }
  return value.replace(/\/+$/, "");
}

const SDK_MODEL_FALLBACK = "claude-sonnet-4-5";
const SDK_SHORT_ALIASES = new Set(["sonnet", "opus", "haiku", "fable"]);
/** LiteLLM /v1/models 只回目录里认识的 Anthropic id；claude-sonnet-5 不会出现在列表里。 */
const SDK_SONNET_MODEL = "claude-sonnet-4-5";
const SDK_OPUS_MODEL = "claude-opus-4-6";
const SDK_HAIKU_MODEL = "claude-haiku-4-5";
const UPSTREAM_HEADER = "X-Qianxun-Upstream-Model";
const UPSTREAM_BASE_HEADER = "X-Qianxun-Upstream-Base-Url";
const UPSTREAM_KEY_HEADER = "X-Qianxun-Upstream-Api-Key";

export function isAnthropicSdkModel(name) {
  const n = String(name || "").trim().toLowerCase();
  return SDK_SHORT_ALIASES.has(n) || n.startsWith("claude-") || n.startsWith("anthropic/");
}

export function isLiteLlmGatewayAlias(name) {
  const n = String(name || "").trim().toLowerCase();
  return n === "openai-default" || n === "openai-compat" || n === "litellm";
}

export function stripLlmProviderPrefix(name) {
  const v = String(name || "").trim();
  const slash = v.indexOf("/");
  if (slash <= 0 || slash >= v.length - 1) {
    return v;
  }
  const provider = v.slice(0, slash).toLowerCase();
  if (provider === "openai" || provider === "anthropic") {
    return v.slice(slash + 1).trim();
  }
  return v;
}

function expandSdkModelAlias(name) {
  const n = String(name || "").trim().toLowerCase();
  if (!n || n === "sonnet" || n === "fable") {
    return SDK_SONNET_MODEL;
  }
  if (n === "opus") {
    return SDK_OPUS_MODEL;
  }
  if (n === "haiku") {
    return SDK_HAIKU_MODEL;
  }
  if (n === "claude-sonnet-5") {
    return SDK_SONNET_MODEL;
  }
  if (n === "claude-opus-5") {
    return SDK_OPUS_MODEL;
  }
  if (n === "claude-haiku-5") {
    return SDK_HAIKU_MODEL;
  }
  return String(name || "").trim() || SDK_MODEL_FALLBACK;
}

export function resolveSdkModel(body) {
  const envSdk = (process.env.QIANXUN_CLAUDE_SDK_MODEL || "").trim();
  const envAnth = (process.env.ANTHROPIC_MODEL || "").trim();
  for (const candidate of [envSdk, envAnth]) {
    if (candidate) {
      return expandSdkModelAlias(candidate);
    }
  }
  return SDK_MODEL_FALLBACK;
}

export function resolveUpstreamModel(body) {
  const fromBody = String(body?.upstreamModel || "").trim();
  const bodyModel = String(body?.model || "").trim();
  const envModel = (process.env.QIANXUN_CLAUDE_MODEL || "").trim();
  const bodyAsUpstream = (isAnthropicSdkModel(bodyModel) || isLiteLlmGatewayAlias(bodyModel))
    ? ""
    : bodyModel;
  for (const candidate of [fromBody, bodyAsUpstream, envModel]) {
    const id = stripLlmProviderPrefix(candidate);
    if (id && !isAnthropicSdkModel(id) && !isLiteLlmGatewayAlias(id)) {
      return id;
    }
  }
  return "";
}

export function resolveUpstreamBaseUrl(body) {
  return String(body?.upstreamBaseUrl || "").trim();
}

export function resolveUpstreamApiKey(body) {
  return String(body?.upstreamApiKey || "").trim();
}

function appendCustomHeader(env, name, value) {
  const v = String(value || "").trim();
  if (!v) {
    return;
  }
  const line = `${name}: ${v}`;
  const prev = (env.ANTHROPIC_CUSTOM_HEADERS || "").trim();
  env.ANTHROPIC_CUSTOM_HEADERS = prev ? `${prev}\n${line}` : line;
}

function sdkProcessEnv(home, sdkModel, upstream) {
  const env = stripProxyEnv({ ...process.env });
  const base = resolveAnthropicBaseUrl(process.env.ANTHROPIC_BASE_URL);
  env.HOME = home;
  env.CLAUDE_CONFIG_DIR = claudeHome(home);
  env.CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC = "1";
  env.ANTHROPIC_BASE_URL = base;
  if ((process.env.ANTHROPIC_API_KEY || "").trim()) {
    env.ANTHROPIC_API_KEY = process.env.ANTHROPIC_API_KEY;
  }
  if ((process.env.ANTHROPIC_AUTH_TOKEN || "").trim()) {
    env.ANTHROPIC_AUTH_TOKEN = process.env.ANTHROPIC_AUTH_TOKEN;
  }
  env.ANTHROPIC_MODEL = expandSdkModelAlias(sdkModel || SDK_MODEL_FALLBACK);
  env.ANTHROPIC_DEFAULT_SONNET_MODEL = SDK_SONNET_MODEL;
  env.ANTHROPIC_DEFAULT_OPUS_MODEL = SDK_OPUS_MODEL;
  env.ANTHROPIC_DEFAULT_HAIKU_MODEL = SDK_HAIKU_MODEL;
  appendCustomHeader(env, UPSTREAM_HEADER, upstream?.model);
  appendCustomHeader(env, UPSTREAM_BASE_HEADER, upstream?.baseUrl);
  appendCustomHeader(env, UPSTREAM_KEY_HEADER, upstream?.apiKey);
  const windowTokens = resolveAutoCompactWindow(upstream?.contextWindow);
  if (windowTokens > 0) {
    env.CLAUDE_CODE_AUTO_COMPACT_WINDOW = String(windowTokens);
  } else {
    delete env.CLAUDE_CODE_AUTO_COMPACT_WINDOW;
  }
  delete env.DISABLE_COMPACT;
  delete env.DISABLE_AUTO_COMPACT;
  return env;
}

/** SDK 按请求体中的真实上游窗口判定 autocompact；未提供则不覆盖。 */
export function resolveAutoCompactWindow(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) {
    return 0;
  }
  return Math.max(8_000, Math.min(1_000_000, Math.round(n)));
}

export function emitCompactIfNeeded(message, writeLine) {
  if (!message || typeof message !== "object" || typeof writeLine !== "function") {
    return;
  }
  if (message.type === "compact") {
    return;
  }
  if (message.type === "system" && message.subtype === "compact_boundary") {
    const meta = message.compact_metadata && typeof message.compact_metadata === "object"
      ? message.compact_metadata
      : {};
    writeLine({
      type: "compact",
      phase: "done",
      trigger: meta.trigger || "auto",
      preTokens: meta.pre_tokens ?? meta.preTokens ?? null,
      session_id: message.session_id || "",
    });
    return;
  }
  if (message.type === "system" && message.subtype === "status" && message.status === "compacting") {
    writeLine({
      type: "compact",
      phase: "start",
      trigger: "",
      preTokens: null,
      session_id: message.session_id || "",
    });
  }
}

function desiredAnthropicBase() {
  return resolveAnthropicBaseUrl(process.env.ANTHROPIC_BASE_URL);
}

function rewriteSettingsObject(current) {
  if (!current || typeof current !== "object" || Array.isArray(current)) {
    return {
      next: {
        env: { ANTHROPIC_BASE_URL: desiredAnthropicBase() },
        autoCompactEnabled: true,
      },
      changed: true,
    };
  }
  const want = desiredAnthropicBase();
  let changed = false;
  const next = { ...current };
  const prevEnv = next.env && typeof next.env === "object" && !Array.isArray(next.env) ? { ...next.env } : {};
  if (prevEnv.ANTHROPIC_BASE_URL !== want) {
    changed = true;
  }
  next.env = {
    ...prevEnv,
    ANTHROPIC_BASE_URL: want,
    ANTHROPIC_DEFAULT_SONNET_MODEL: SDK_SONNET_MODEL,
    ANTHROPIC_DEFAULT_OPUS_MODEL: SDK_OPUS_MODEL,
    ANTHROPIC_DEFAULT_HAIKU_MODEL: SDK_HAIKU_MODEL,
  };
  if (next.autoCompactEnabled !== true) {
    next.autoCompactEnabled = true;
    changed = true;
  }
  changed = true;
  const apiKey = (process.env.ANTHROPIC_API_KEY || process.env.ANTHROPIC_AUTH_TOKEN || "").trim();
  if (apiKey && prevEnv.ANTHROPIC_API_KEY !== apiKey) {
    next.env.ANTHROPIC_API_KEY = apiKey;
  }
  if (apiKey && prevEnv.ANTHROPIC_AUTH_TOKEN !== apiKey) {
    next.env.ANTHROPIC_AUTH_TOKEN = apiKey;
  }
  if (typeof next.ANTHROPIC_BASE_URL === "string") {
    delete next.ANTHROPIC_BASE_URL;
  }
  return { next, changed };
}

async function rewriteClaudeSettingsFile(file) {
  let raw;
  try {
    raw = await fs.readFile(file, "utf8");
  } catch {
    return false;
  }
  let current;
  try {
    current = JSON.parse(raw);
  } catch {
    return false;
  }
  const { next, changed } = rewriteSettingsObject(current);
  if (!changed) {
    return false;
  }
  await fs.writeFile(file, JSON.stringify(next, null, 2), "utf8");
  console.warn(`[claude-code] 已改写 ${file} 的 ANTHROPIC_BASE_URL → ${desiredAnthropicBase()}`);
  return true;
}

async function ensureAnthropicBaseInSettings(home, userId, cwd) {
  const dir = claudeHome(home);
  await fs.mkdir(dir, { recursive: true });
  const files = [
    path.join(dir, "settings.json"),
    path.join(dir, ".claude.json"),
  ];
  if (userId) {
    files.push(path.join(workspace(userId), ".claude", "settings.json"));
  }
  if (cwd) {
    files.push(path.join(cwd, ".claude", "settings.json"));
  }
  for (const file of files) {
    try {
      await fs.access(file);
      await rewriteClaudeSettingsFile(file);
    } catch {
      if (file.endsWith(`${path.sep}settings.json`) && file.includes(`${path.sep}.claude${path.sep}`)) {
        const { next } = rewriteSettingsObject({});
        await fs.mkdir(path.dirname(file), { recursive: true });
        await fs.writeFile(file, JSON.stringify(next, null, 2), "utf8");
      }
    }
  }
}

/** 启动时扫所有用户 profile / 工作区里的 Claude settings，去掉 127.0.0.1:3456 一类本机地址 */
export async function migrateAllProfileClaudeSettings() {
  let users;
  try {
    users = await fs.readdir(process.env.DATA_DIR || "/opt/data", { withFileTypes: true });
  } catch {
    return 0;
  }
  let count = 0;
  const root = process.env.DATA_DIR || "/opt/data";
  for (const u of users) {
    if (!u.isDirectory() || u.name.startsWith(".") || u.name.startsWith("_")) {
      continue;
    }
    const files = [
      path.join(root, u.name, "workspace", ".claude", "settings.json"),
    ];
    let profiles;
    try {
      profiles = await fs.readdir(path.join(root, u.name, "profiles"), { withFileTypes: true });
    } catch {
      profiles = [];
    }
    for (const p of profiles) {
      if (!p.isDirectory()) {
        continue;
      }
      const claudeDir = path.join(root, u.name, "profiles", p.name, ".claude");
      files.push(path.join(claudeDir, "settings.json"), path.join(claudeDir, ".claude.json"));
    }
    for (const file of files) {
      if (await rewriteClaudeSettingsFile(file)) {
        count += 1;
      }
    }
  }
  return count;
}

/**
 * @param {unknown} bodyThinking
 * @param {string|undefined} envThinking
 * @returns {{ type: string, budgetTokens?: number } | null}
 */
function resolveThinkingOption(bodyThinking, envThinking) {
  if (bodyThinking && typeof bodyThinking === "object" && bodyThinking.type) {
    return bodyThinking;
  }
  const raw = (envThinking || "").trim().toLowerCase();
  if (!raw) {
    return null;
  }
  if (raw === "disabled" || raw === "off" || raw === "0" || raw === "false") {
    return { type: "disabled" };
  }
  if (raw === "adaptive" || raw === "auto") {
    return { type: "adaptive" };
  }
  if (raw === "enabled" || raw === "on" || raw === "true") {
    return { type: "enabled", budgetTokens: 10000 };
  }
  if (/^\d+$/.test(raw)) {
    return { type: "enabled", budgetTokens: Number(raw) };
  }
  const m = raw.match(/^enabled(?::|\/|=)?(\d+)$/);
  if (m) {
    return { type: "enabled", budgetTokens: Number(m[1]) };
  }
  return null;
}

function transcript(history) {
  if (!Array.isArray(history) || history.length === 0) {
    return "";
  }
  const from = Math.max(0, history.length - 12);
  const lines = [];
  for (let i = from; i < history.length; i++) {
    const m = history[i];
    if (!m) {
      continue;
    }
    const content = (m.content || "").replace(/\s+/g, " ").trim();
    if (!content) {
      continue;
    }
    const label = m.role === "assistant" ? "助手" : "用户";
    lines.push(`${label}：${content.length > 800 ? content.slice(0, 800) + "…" : content}`);
  }
  return lines.join("\n");
}

async function readSessionId(cwd, cacheKey, userId) {
  try {
    const id = (await fs.readFile(sessionFile(cwd, cacheKey), "utf8")).trim();
    if (id && id.length <= 128) {
      return id;
    }
  } catch {
    /* 新 cwd 尚无映射 */
  }
  if (!userId || !cacheKey) {
    return "";
  }
  const legacyRoots = [workspace(userId), legacySessionCwd(userId, cacheKey)];
  for (const root of legacyRoots) {
    try {
      const legacy = sessionFile(root, cacheKey);
      const id = (await fs.readFile(legacy, "utf8")).trim();
      if (id && id.length <= 128) {
        await writeSessionId(cwd, cacheKey, id);
        return id;
      }
    } catch {
      /* 无旧映射 */
    }
  }
  return "";
}

async function writeSessionId(cwd, cacheKey, sessionId) {
  if (!sessionId) {
    return;
  }
  const file = sessionFile(cwd, cacheKey);
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, sessionId.trim(), "utf8");
}

export async function streamTurn(body, writeLine, signal) {
  const profile = body.profile || "default";
  const userId = sanitizeOwnerId(body.userId || "");
  if (!userId) {
    throw new Error("缺少或无效 userId");
  }
  const sessionId = body.sessionId || "";
  await createProfile(userId, profile, "");
  const home = profileHome(profile, userId);
  const cwd = sessionCwd(userId, body.workspaceSessionId || sessionId, profile);
  await fs.mkdir(cwd, { recursive: true });
  await fs.mkdir(claudeHome(home), { recursive: true });
  await migrateLegacyClaudeHome(home);
  await ensureDiscoverableSkillsLayout(home);

  const resumeId = await readSessionId(cwd, sessionId, userId);
  let prompt = body.prompt == null ? "" : String(body.prompt);
  let historyAppend = "";
  if (!resumeId) {
    const seeded = applySeedHistory(prompt, transcript(body.seedHistory));
    prompt = seeded.prompt;
    historyAppend = seeded.historyAppend;
  }

  const gw = await readToolsets(userId, profile);
  const rawEnabled = Array.isArray(body.allowedToolsets) && body.allowedToolsets.length
    ? body.allowedToolsets
    : (gw.enabled && gw.enabled.length ? gw.enabled : DEFAULT_ENABLED);
  const enabled = expandEnabledToolsets(rawEnabled, gw.disabled || []);
  let tools = allowedClaudeTools(enabled);
  let disallowed = disallowedClaudeTools(enabled);
  const skills = await listSkills(userId, profile);
  const enabledSkillInfos = skills.filter((s) => s.enabled);
  const enabledSkills = [...new Set(enabledSkillInfos.flatMap((s) => (
    Array.isArray(s.invokeNames) && s.invokeNames.length ? s.invokeNames : [s.name]
  )))];
  const skillsOn = enabled.some((n) => String(n).toLowerCase() === "skills");
  const skillToolOn = skillToolEnabled(skillsOn, enabledSkills);
  const slashDispatch = isSlashPrompt(prompt);
  if (!skillToolOn && !slashDispatch) {
    tools = tools.filter((t) => t !== "Skill");
    disallowed = [...new Set([...disallowed, "Skill"])];
  } else if (slashDispatch && !tools.includes("Skill")) {
    tools = [...tools, "Skill"];
    disallowed = disallowed.filter((t) => t !== "Skill");
  }

  const enabledSet = lowerSet(enabled);
  const disabledSet = lowerSet(gw.disabled || []);
  const mcpBlocked = body.mcpDisabled === true
    || enabledSet.has("no_mcp")
    || disabledSet.has("no_mcp");

  let mcpServers = {};
  let mcpAllowedTools = [];
  if (!mcpBlocked) {
    const rawServers = await listMcpServers(userId, profile);
    const built = buildSdkMcpConfig(rawServers);
    mcpServers = built.servers;
    mcpAllowedTools = built.allowedTools;
    if (Object.keys(mcpServers).length) {
      await syncMcpJsonFile(home, mcpServers);
    }
  }

  const rawPlugins = await listPlugins(userId, profile);
  const enabledPlugins = rawPlugins.filter((p) => p.enabled !== false);
  await syncPluginsManifest(home, enabledPlugins);

  const permissionMode = body.permissionMode || process.env.QIANXUN_CLAUDE_PERMISSION_MODE || "bypassPermissions";
  const model = resolveSdkModel(body);
  const upstreamModel = resolveUpstreamModel(body);
  const upstreamBaseUrl = resolveUpstreamBaseUrl(body);
  const upstreamApiKey = resolveUpstreamApiKey(body);
  const soul = await readProfileSoul(home);
  const officerMcp = buildOfficerMcp(body.orchestration);
  const officerAppend = officerMcp ? officerHint(body.orchestration) : "";
  const memory = await memoryRecall({
    userId,
    prompt,
    body,
    officer: Boolean(officerMcp),
  });
  if (memory.append || memory.degraded || memory.reason !== "disabled") {
    console.log(
      `[memory] recall reason=${memory.reason} hits=${memory.hits.length}`
      + ` degraded=${memory.degraded} officer=${Boolean(officerMcp)}`,
    );
  }
  const memoryBlock = memory.append ? `\n\n${memory.append}` : "";
  let append = officerAppend + buildSystemAppend(cwd, soul, enabledSkillInfos) + memoryBlock + historyAppend;
  if (officerMcp) {
    tools = withOfficerMcpTools(applyOfficerLeanTools(tools, { skillsOn: skillToolOn || slashDispatch }));
    disallowed = [...new Set([
      ...disallowedClaudeToolsFromAllowlist(tools.filter((t) => !String(t).startsWith("mcp__"))),
      ...officerBuiltinDelegationTools(),
    ])];
    mcpServers = { ...mcpServers, "qianxun-officer": officerMcp };
    mcpAllowedTools = [...mcpAllowedTools, ...officerAllowedTools()];
    const agentCodes = Array.isArray(body.orchestration?.agents)
      ? body.orchestration.agents.map((a) => (a && a.code ? String(a.code) : "")).filter(Boolean)
      : [];
    console.log(`[claude-code] officer mcp on agents=${agentCodes.join(",") || "-"} tools=${tools.join(",")}`);
  }

  const abortController = new AbortController();
  if (signal) {
    if (signal.aborted) {
      abortController.abort();
    } else {
      signal.addEventListener("abort", () => abortController.abort(), { once: true });
    }
  }

  const allowedTools = [...tools, ...mcpAllowedTools];
  const pluginHint = enabledPlugins.length
    ? `\n【已启用插件】${enabledPlugins.map((p) => p.name).join("、")}`
    : "";
  await ensureAnthropicBaseInSettings(home, userId, cwd);
  const compactWindow = resolveAutoCompactWindow(body?.contextWindow);
  const sdkEnv = sdkProcessEnv(home, model, {
    model: upstreamModel,
    baseUrl: upstreamBaseUrl,
    apiKey: upstreamApiKey,
    contextWindow: compactWindow,
  });
  console.log(`[claude-code] sdk api=${sdkEnv.ANTHROPIC_BASE_URL} model=${model} upstream=${upstreamModel || "-"} base=${upstreamBaseUrl || "-"} key=${upstreamApiKey ? "set" : "unset"} compactWindow=${compactWindow} proxy=${sdkEnv.HTTP_PROXY === undefined ? "unset" : "set"}`);
  const options = {
    cwd,
    abortController,
    model,
    permissionMode,
    allowDangerouslySkipPermissions: permissionMode === "bypassPermissions",
    includePartialMessages: true,
    persistSession: true,
    settingSources: SDK_SETTING_SOURCES,
    // 子任务正文/工具带 parent_tool_use_id，供前端挂到 Agent 卡片下
    forwardSubagentText: true,
    systemPrompt: buildSystemPrompt({
      officer: Boolean(officerMcp),
      append,
      pluginHint,
    }),
    tools,
    allowedTools,
    disallowedTools: disallowed,
    env: sdkEnv,
  };
  // OpenAI Compatible 上游经 LiteLLM 时，extended thinking 会触发 /v1/responses
  // 并带上不兼容的 thinking_budget；默认关闭。可用 QIANXUN_CLAUDE_THINKING 覆盖。
  const thinkingOpt = resolveThinkingOption(body.thinking, process.env.QIANXUN_CLAUDE_THINKING);
  if (thinkingOpt) {
    options.thinking = thinkingOpt;
  }
  if (resumeId) {
    options.resume = resumeId;
  }
  if (skillToolOn) {
    options.skills = enabledSkills;
  }
  console.log(`[claude-code] skills on=${skillToolOn} names=${enabledSkills.join(",") || "-"} home=${home}`);
  if (Object.keys(mcpServers).length) {
    options.mcpServers = mcpServers;
    options.strictMcpConfig = true;
  }

  let lastSession = resumeId;
  let sawResult = false;
  let turnOk = false;
  const assistantState = { text: "", fromStream: false };
  const q = query({ prompt, options });
  try {
    for await (const message of q) {
      if (message && message.type === "system" && message.subtype === "init") {
        const loaded = Array.isArray(message.skills) ? message.skills : [];
        const names = loaded.map((s) => (typeof s === "string" ? s : (s && s.name) || "")).filter(Boolean);
        console.log(`[claude-code] sdk init skills=${names.join(",") || "-"}`);
      }
      if (message && message.session_id) {
        lastSession = message.session_id;
      }
      if (message && message.type === "result" && !message.parent_tool_use_id && !message.parentToolUseId) {
        sawResult = true;
        turnOk = !(message.is_error || message.subtype === "error");
      }
      accumulateAssistantText(assistantState, message);
      writeLine(message);
      emitCompactIfNeeded(message, writeLine);
    }
    await emitContextUsageSnapshot(q, writeLine, lastSession);
  } catch (ex) {
    if (abortController.signal.aborted) {
      writeLine({ type: "error", error: "客户端取消或连接中断" });
      throw ex;
    }
    writeLine({ type: "error", error: ex?.message || String(ex) });
    throw ex;
  }
  if (lastSession) {
    await writeSessionId(cwd, sessionId, lastSession);
  }
  if (!sawResult) {
    turnOk = true;
    writeLine({
      type: "result",
      subtype: "success",
      session_id: lastSession || "",
      is_error: false,
    });
  }

  // Phase 2：异步固化，不 await 完成，避免拖慢响应
  try {
    const persisted = await memoryPersist({
      userId,
      prompt,
      assistantText: assistantState.text,
      body,
      officer: Boolean(officerMcp),
      ok: turnOk,
      sessionId: lastSession || sessionId || "",
    });
    if (persisted.reason !== "disabled") {
      console.log(
        `[memory] persist reason=${persisted.reason} jobs=${persisted.jobs}`
        + ` enqueued=${persisted.enqueued} chars=${String(assistantState.text || "").length}`,
      );
    }
  } catch (err) {
    console.warn(`[memory] persist schedule failed: ${err?.message || err}`);
  }
}
