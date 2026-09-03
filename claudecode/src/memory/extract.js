/**
 * 从 Claude Agent SDK 流式消息中提取本轮助手正文（顶层，不含子任务）。
 */

/**
 * @param {object} message
 * @returns {string}
 */
export function extractAssistantDelta(message) {
  if (!message || typeof message !== "object") {
    return "";
  }
  if (message.parent_tool_use_id || message.parentToolUseId) {
    return "";
  }
  const type = message.type;
  if (type === "stream_event") {
    const event = message.event || {};
    const delta = event.delta || {};
    if (delta.type === "text_delta" && delta.text) {
      return String(delta.text);
    }
    return "";
  }
  if (type === "assistant") {
    return extractTextBlocks(message.message?.content ?? message.content);
  }
  if (type === "result") {
    // result 字段常为整轮摘要；仅在尚未从 assistant/delta 收到正文时作为兜底
    return String(message.result || "").trim();
  }
  return "";
}

/**
 * @param {unknown} content
 * @returns {string}
 */
export function extractTextBlocks(content) {
  if (typeof content === "string") {
    return content;
  }
  if (!Array.isArray(content)) {
    return "";
  }
  const parts = [];
  for (const block of content) {
    if (!block || typeof block !== "object") {
      continue;
    }
    if (block.type === "text" && block.text) {
      parts.push(String(block.text));
    }
  }
  return parts.join("");
}

/**
 * 累积助手正文；若本轮已有流式/assistant 文本，则忽略 result 兜底，避免重复。
 *
 * @param {{ text: string, fromStream: boolean }} state
 * @param {object} message
 */
export function accumulateAssistantText(state, message) {
  if (!state || typeof state !== "object") {
    return;
  }
  const type = message?.type;
  if (type === "result") {
    if (state.fromStream && String(state.text || "").trim()) {
      return;
    }
    const r = extractAssistantDelta(message);
    if (r) {
      state.text = r;
    }
    return;
  }
  const delta = extractAssistantDelta(message);
  if (!delta) {
    return;
  }
  state.text = `${state.text || ""}${delta}`;
  state.fromStream = true;
}
