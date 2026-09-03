/**
 * Mem0 语义记忆配置（Phase 1：只读召回）。
 * 环境变量优先；请求体可临时开关 memoryEnabled。
 *
 * mode:
 * - platform → 云端 api.mem0.ai（/v1/memories/search/ + Token）
 * - oss      → 本地自托管（POST /search，可无鉴权）
 */

export const PREFS_AGENT_ID = "user_prefs";

export function truthyEnv(raw) {
  const v = String(raw ?? "").trim().toLowerCase();
  return v === "1" || v === "true" || v === "yes" || v === "on";
}

export function falsyEnv(raw) {
  const v = String(raw ?? "").trim().toLowerCase();
  return v === "0" || v === "false" || v === "no" || v === "off";
}

/**
 * @param {object} [body]
 * @param {NodeJS.ProcessEnv} [env]
 */
export function loadMemoryConfig(body = {}, env = process.env) {
  const rawMode = String(env.QIANXUN_MEM0_MODE || env.MEM0_MODE || "").trim().toLowerCase();
  let baseUrl = String(env.MEM0_BASE_URL || env.QIANXUN_MEM0_BASE_URL || "")
    .trim()
    .replace(/\/+$/, "");

  let mode = rawMode === "oss" || rawMode === "platform" ? rawMode : "";
  if (!mode) {
    if (baseUrl && !/api\.mem0\.ai/i.test(baseUrl)) {
      mode = "oss";
    } else {
      mode = "platform";
    }
  }
  if (!baseUrl) {
    baseUrl = mode === "oss" ? "http://mem0:8000" : "https://api.mem0.ai";
  }

  let apiKey = String(env.MEM0_API_KEY || env.QIANXUN_MEM0_API_KEY || "").trim();
  if (!apiKey && mode === "oss") {
    apiKey = "oss-local";
  }

  const appId = String(env.QIANXUN_MEM0_APP_ID || env.MEM0_APP_ID || "qianxun").trim() || "qianxun";
  const timeoutMs = clampInt(env.QIANXUN_MEM0_TIMEOUT_MS, 250, 50, 5_000);
  const topK = clampInt(env.QIANXUN_MEM0_TOP_K, 5, 1, 20);
  const maxChars = clampInt(env.QIANXUN_MEM0_MAX_CHARS, 4_000, 500, 12_000);

  const hasCredential = mode === "oss" ? Boolean(apiKey) : Boolean(apiKey) && apiKey !== "oss-local";
  let enabled = hasCredential && truthyEnv(env.QIANXUN_MEM0_ENABLED);
  if (mode === "oss" && truthyEnv(env.QIANXUN_MEM0_ENABLED)) {
    enabled = true;
  }
  if (falsyEnv(env.QIANXUN_MEM0_ENABLED)) {
    enabled = false;
  }
  if (body && body.memoryEnabled === false) {
    enabled = false;
  } else if (body && body.memoryEnabled === true) {
    enabled = mode === "oss" ? true : Boolean(apiKey) && apiKey !== "oss-local";
  }

  return {
    enabled,
    mode,
    apiKey,
    baseUrl,
    appId,
    timeoutMs,
    topK,
    maxChars,
    prefsAgentId: PREFS_AGENT_ID,
    /** OSS 不保证 app_id 过滤 */
    includeAppId: mode === "platform",
  };
}

function clampInt(raw, fallback, min, max) {
  const n = Number.parseInt(String(raw ?? ""), 10);
  if (!Number.isFinite(n)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, n));
}
