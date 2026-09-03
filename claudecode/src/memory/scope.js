import { PREFS_AGENT_ID } from "./config.js";

/**
 * 解析本轮专业 Agent 车道 ID。
 * 干警调度轮次默认不走专业车道（仅个人偏好），避免串记忆。
 *
 * @param {object} opts
 * @param {string} opts.userId
 * @param {object} [opts.body]
 * @param {boolean} [opts.officer]
 * @param {string} [opts.appId]
 * @param {string} [opts.prefsAgentId]
 */
export function resolveMemoryScope({
  userId,
  body = {},
  officer = false,
  appId = "qianxun",
  prefsAgentId = PREFS_AGENT_ID,
} = {}) {
  const uid = String(userId || "").trim();
  const app = String(appId || "qianxun").trim() || "qianxun";
  const prefs = String(prefsAgentId || PREFS_AGENT_ID).trim() || PREFS_AGENT_ID;

  let agentId = "";
  if (!officer) {
    agentId = String(
      body.agentInstanceId
      || body.agentId
      || body.memoryAgentId
      || "",
    ).trim();
  }

  return {
    userId: uid,
    appId: app,
    prefsAgentId: prefs,
    agentId,
    /** 是否召回专业 Agent 车道 */
    includeAgentLane: Boolean(uid && agentId),
    /** 是否召回个人偏好车道 */
    includePrefsLane: Boolean(uid),
  };
}

/**
 * Mem0 Platform v3 / OSS filters（实体 ID 必须可用）。
 * @param {object} scope
 * @param {"prefs"|"agent"} lane
 * @param {{ includeAppId?: boolean }} [opts]
 */
export function buildLaneFilters(scope, lane, opts = {}) {
  const userId = scope?.userId;
  if (!userId) {
    return null;
  }
  const filters = { user_id: userId };
  if (opts.includeAppId !== false && scope.appId) {
    filters.app_id = scope.appId;
  }
  if (lane === "prefs") {
    filters.agent_id = scope.prefsAgentId || PREFS_AGENT_ID;
  } else if (lane === "agent") {
    if (!scope.agentId) {
      return null;
    }
    filters.agent_id = scope.agentId;
  } else {
    return null;
  }
  return filters;
}
