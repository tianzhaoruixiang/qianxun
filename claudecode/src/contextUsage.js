/**
 * 轮末调用 SDK getContextUsage，产出与 CLI /context 同源的上下文快照。
 * @param {import('@anthropic-ai/claude-agent-sdk').Query} q
 * @param {(obj: Record<string, unknown>) => void} writeLine
 * @param {string} sessionId
 */
export async function emitContextUsageSnapshot(q, writeLine, sessionId) {
  if (!q || typeof q.getContextUsage !== "function" || typeof writeLine !== "function") {
    return;
  }
  try {
    const ctx = await q.getContextUsage({ detail: "summary" });
    if (!ctx || typeof ctx !== "object") {
      return;
    }
    const apiUsage = ctx.apiUsage && typeof ctx.apiUsage === "object" ? ctx.apiUsage : null;
    writeLine({
      type: "context_usage",
      session_id: sessionId || "",
      totalTokens: ctx.totalTokens ?? null,
      maxTokens: ctx.maxTokens ?? ctx.rawMaxTokens ?? null,
      percentage: ctx.percentage ?? null,
      model: ctx.model ?? null,
      apiUsage: apiUsage
        ? {
          input_tokens: apiUsage.input_tokens ?? null,
          output_tokens: apiUsage.output_tokens ?? null,
          cache_creation_input_tokens: apiUsage.cache_creation_input_tokens ?? null,
          cache_read_input_tokens: apiUsage.cache_read_input_tokens ?? null,
        }
        : null,
    });
  } catch (ex) {
    console.warn(`[claude-code] getContextUsage skipped: ${ex?.message || ex}`);
  }
}

/** @param {unknown} modelUsage */
export function sumModelUsageTokens(modelUsage) {
  if (!modelUsage || typeof modelUsage !== "object" || Array.isArray(modelUsage)) {
    return { input: 0, output: 0 };
  }
  let input = 0;
  let output = 0;
  for (const value of Object.values(modelUsage)) {
    if (!value || typeof value !== "object") {
      continue;
    }
    const inTok = Number(value.inputTokens ?? value.input_tokens ?? 0);
    const outTok = Number(value.outputTokens ?? value.output_tokens ?? 0);
    if (Number.isFinite(inTok) && inTok > 0) {
      input += inTok;
    }
    if (Number.isFinite(outTok) && outTok > 0) {
      output += outTok;
    }
  }
  return { input, output };
}
