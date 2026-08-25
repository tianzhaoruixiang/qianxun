import fs from "node:fs/promises";
import path from "node:path";
import { query } from "@anthropic-ai/claude-agent-sdk";
import {
  claudeHome,
  claudeMd,
  profileHome,
  sessionFile,
  soulMd,
  workspace,
} from "./paths.js";
import { allowedClaudeTools, DEFAULT_ENABLED, disallowedClaudeTools, expandEnabledToolsets, lowerSet } from "./toolsets.js";
import { createProfile, ensureDiscoverableSkillsLayout, listSkills, migrateLegacyClaudeHome, readToolsets } from "./store.js";
import {
  buildSdkMcpConfig,
  listMcpServers,
  listPlugins,
  syncMcpJsonFile,
  syncPluginsManifest,
} from "./mcp.js";
import { sanitizeOwnerId } from "./paths.js";

const DOC_HINT = "【工作区规则】每个用户有独立 cwd，同一用户的多个会话共享该目录。"
  + "文件/终端操作只能使用该 cwd 下的相对路径；"
  + "不要使用绝对路径访问其它目录，不要 ls/读取父目录（..）或同级其它 qx 用户目录。"
  + "本容器是火山 AIO 智能体沙箱：可直接运行 python / python3"
  + "（已预装 pandas、numpy、openpyxl、python-docx、matplotlib 等），不要 pip install。"
  + "若生成 xlsx/md/doc/docx，用 Write 或 python 写入当前 cwd（普通文件名如 report.xlsx）。"
  + "不要编造 /QianXunService/data/files/public/ 链接，也不要把 Docker 内部主机名发给用户。"
  + "平台会自动入库并追加可点击链接。";
const SOUL_APPEND_MAX = 80_000;

async function readProfileSoul(home) {
  for (const file of [claudeMd(home), soulMd(home)]) {
    try {
      const text = (await fs.readFile(file, "utf8")).trim();
      if (text) {
        return text.length > SOUL_APPEND_MAX ? text.slice(0, SOUL_APPEND_MAX) : text;
      }
    } catch {
      /* next */
    }
  }
  return "";
}

function buildSystemAppend(cwd, soul, enabledSkillInfos) {
  const fence = `${DOC_HINT} 唯一允许的工作目录是：${cwd}。`;
  const skills = Array.isArray(enabledSkillInfos) ? enabledSkillInfos : [];
  const skillHint = skills.length
    ? `\n【已启用技能】${skills.map((s) => {
      const name = s && s.name ? String(s.name) : "";
      const desc = s && s.description ? String(s.description).replace(/\s+/g, " ").trim() : "";
      return desc ? `${name}（${desc}）` : name;
    }).filter(Boolean).join("；")}。需要时调用 Skill 工具并传入对应技能名，按该技能的 SKILL.md 执行。`
    : "";
  if (!soul) {
    return fence + skillHint;
  }
  return `【智能体灵魂】\n${soul}\n\n${fence}${skillHint}`;
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

function sdkProcessEnv(home) {
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
  if ((process.env.ANTHROPIC_MODEL || "").trim()) {
    env.ANTHROPIC_MODEL = process.env.ANTHROPIC_MODEL;
  }
  return env;
}

function desiredAnthropicBase() {
  return resolveAnthropicBaseUrl(process.env.ANTHROPIC_BASE_URL);
}

function rewriteSettingsObject(current) {
  if (!current || typeof current !== "object" || Array.isArray(current)) {
    return { next: { env: { ANTHROPIC_BASE_URL: desiredAnthropicBase() } }, changed: true };
  }
  const want = desiredAnthropicBase();
  let changed = false;
  const next = { ...current };
  const prevEnv = next.env && typeof next.env === "object" && !Array.isArray(next.env) ? { ...next.env } : {};
  if (prevEnv.ANTHROPIC_BASE_URL !== want) {
    changed = true;
  }
  next.env = { ...prevEnv, ANTHROPIC_BASE_URL: want };
  const apiKey = (process.env.ANTHROPIC_API_KEY || process.env.ANTHROPIC_AUTH_TOKEN || "").trim();
  if (apiKey && prevEnv.ANTHROPIC_API_KEY !== apiKey) {
    next.env.ANTHROPIC_API_KEY = apiKey;
    changed = true;
  }
  if (apiKey && prevEnv.ANTHROPIC_AUTH_TOKEN !== apiKey) {
    next.env.ANTHROPIC_AUTH_TOKEN = apiKey;
    changed = true;
  }
  if (typeof next.ANTHROPIC_BASE_URL === "string") {
    delete next.ANTHROPIC_BASE_URL;
    changed = true;
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

async function ensureAnthropicBaseInSettings(home, userId) {
  const dir = claudeHome(home);
  await fs.mkdir(dir, { recursive: true });
  const files = [
    path.join(dir, "settings.json"),
    path.join(dir, ".claude.json"),
  ];
  if (userId) {
    files.push(path.join(workspace(userId), ".claude", "settings.json"));
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

async function readSessionId(cwd, cacheKey) {
  try {
    const id = (await fs.readFile(sessionFile(cwd, cacheKey), "utf8")).trim();
    return id.length > 128 ? "" : id;
  } catch {
    return "";
  }
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
  const cwd = workspace(userId);
  const home = profileHome(profile, userId);
  await fs.mkdir(cwd, { recursive: true });
  await fs.mkdir(claudeHome(home), { recursive: true });
  await migrateLegacyClaudeHome(home);
  await ensureDiscoverableSkillsLayout(home);

  const resumeId = await readSessionId(cwd, sessionId);
  let prompt = body.prompt == null ? "" : String(body.prompt);
  if (!resumeId) {
    const hist = transcript(body.seedHistory);
    if (hist) {
      prompt = `【会话历史】\n${hist}\n【本轮】\n${prompt}`;
    }
  }

  const gw = await readToolsets(userId, profile);
  const rawEnabled = Array.isArray(body.allowedToolsets) && body.allowedToolsets.length
    ? body.allowedToolsets
    : (gw.enabled && gw.enabled.length ? gw.enabled : DEFAULT_ENABLED);
  const enabled = expandEnabledToolsets(rawEnabled, gw.disabled || []);
  const tools = allowedClaudeTools(enabled);
  const disallowed = disallowedClaudeTools(enabled);
  const skills = await listSkills(userId, profile);
  const enabledSkillInfos = skills.filter((s) => s.enabled);
  const enabledSkills = enabledSkillInfos.map((s) => s.name);
  const skillsOn = enabled.some((n) => String(n).toLowerCase() === "skills");

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
  const model = body.model || process.env.QIANXUN_CLAUDE_MODEL || process.env.ANTHROPIC_MODEL || "qwen3.6-plus";
  const soul = await readProfileSoul(home);
  const append = buildSystemAppend(cwd, soul, enabledSkillInfos);

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
  await ensureAnthropicBaseInSettings(home, userId);
  const sdkEnv = sdkProcessEnv(home);
  console.log(`[claude-code] sdk api=${sdkEnv.ANTHROPIC_BASE_URL} model=${model} proxy=${sdkEnv.HTTP_PROXY === undefined ? "unset" : "set"}`);
  const options = {
    cwd,
    abortController,
    model,
    permissionMode,
    allowDangerouslySkipPermissions: permissionMode === "bypassPermissions",
    includePartialMessages: true,
    persistSession: true,
    settingSources: ["user", "project"],
    systemPrompt: { type: "preset", preset: "claude_code", append: append + pluginHint },
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
  if (skillsOn && enabledSkills.length) {
    options.skills = enabledSkills;
  } else if (skillsOn) {
    options.skills = [];
  }
  console.log(`[claude-code] skills on=${skillsOn} names=${enabledSkills.join(",") || "-"} home=${home}`);
  if (Object.keys(mcpServers).length) {
    options.mcpServers = mcpServers;
    options.strictMcpConfig = true;
  }

  let lastSession = resumeId;
  let sawResult = false;
  try {
    for await (const message of query({ prompt, options })) {
      if (message && message.session_id) {
        lastSession = message.session_id;
      }
      if (message && message.type === "result") {
        sawResult = true;
      }
      writeLine(message);
    }
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
    writeLine({
      type: "result",
      subtype: "success",
      session_id: lastSession || "",
      is_error: false,
    });
  }
}
