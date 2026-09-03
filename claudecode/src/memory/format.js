/**
 * 将 Mem0 召回结果格式化为 system append 片段。
 */

/**
 * @param {Array<{ id?: string, memory?: string, text?: string, score?: number, lane?: string }>} items
 * @param {{ maxChars?: number }} [opts]
 * @returns {string}
 */
export function formatMemoryAppend(items, opts = {}) {
  const maxChars = Number.isFinite(opts.maxChars) ? opts.maxChars : 4_000;
  const list = Array.isArray(items) ? items : [];
  const lines = [];
  const seen = new Set();

  for (const item of list) {
    const text = String(item?.memory ?? item?.text ?? "").trim().replace(/\s+/g, " ");
    if (!text) {
      continue;
    }
    const key = text.toLowerCase();
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    const lane = item.lane === "prefs" ? "偏好" : (item.lane === "agent" ? "专长" : "");
    lines.push(lane ? `- （${lane}）${text}` : `- ${text}`);
  }

  if (!lines.length) {
    return "";
  }

  const header = "【长期记忆】以下为与当前问题相关的既有事实，请遵守；若与用户本轮表述冲突，以本轮为准。\n";
  let body = lines.join("\n");
  let out = header + body;
  if (out.length <= maxChars) {
    return out;
  }

  // 超长时按行裁剪，保留 header
  const kept = [];
  let used = header.length;
  for (const line of lines) {
    const next = used + line.length + (kept.length ? 1 : 0);
    if (next > maxChars) {
      break;
    }
    kept.push(line);
    used = next;
  }
  if (!kept.length) {
    const budget = Math.max(0, maxChars - header.length);
    return budget > 0 ? header + lines[0].slice(0, budget) : "";
  }
  return header + kept.join("\n");
}

/**
 * 合并双车道结果：按 score 降序，去重，截断条数。
 */
export function mergeMemoryHits(prefsHits, agentHits, { topK = 5 } = {}) {
  const tagged = [
    ...(Array.isArray(prefsHits) ? prefsHits.map((h) => ({ ...h, lane: "prefs" })) : []),
    ...(Array.isArray(agentHits) ? agentHits.map((h) => ({ ...h, lane: "agent" })) : []),
  ];
  tagged.sort((a, b) => (Number(b.score) || 0) - (Number(a.score) || 0));

  const out = [];
  const seen = new Set();
  const limit = Math.max(1, topK | 0);
  for (const hit of tagged) {
    const text = String(hit?.memory ?? hit?.text ?? "").trim().toLowerCase();
    if (!text || seen.has(text)) {
      continue;
    }
    seen.add(text);
    out.push(hit);
    if (out.length >= limit) {
      break;
    }
  }
  return out;
}
