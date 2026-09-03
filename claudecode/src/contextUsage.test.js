import test from "node:test";
import assert from "node:assert/strict";
import { emitContextUsageSnapshot, sumModelUsageTokens } from "./contextUsage.js";

test("sumModelUsageTokens aggregates per-model entries", () => {
  const totals = sumModelUsageTokens({
    "claude-sonnet-4-5": { inputTokens: 100, outputTokens: 20 },
    "claude-haiku-4-5": { input_tokens: 50, output_tokens: 10 },
  });
  assert.equal(totals.input, 150);
  assert.equal(totals.output, 30);
});

test("emitContextUsageSnapshot writes context_usage NDJSON", async () => {
  const lines = [];
  const q = {
    async getContextUsage() {
      return {
        totalTokens: 42_000,
        maxTokens: 200_000,
        percentage: 21,
        model: "claude-sonnet-4-5",
        apiUsage: {
          input_tokens: 40_000,
          output_tokens: 2_000,
          cache_read_input_tokens: 8_000,
          cache_creation_input_tokens: 1_000,
        },
      };
    },
  };
  await emitContextUsageSnapshot(q, (obj) => lines.push(obj), "sess-1");
  assert.equal(lines.length, 1);
  assert.equal(lines[0].type, "context_usage");
  assert.equal(lines[0].session_id, "sess-1");
  assert.equal(lines[0].totalTokens, 42_000);
  assert.equal(lines[0].apiUsage.cache_read_input_tokens, 8_000);
});

test("emitContextUsageSnapshot tolerates missing getContextUsage", async () => {
  const lines = [];
  await emitContextUsageSnapshot({}, (obj) => lines.push(obj), "s");
  assert.equal(lines.length, 0);
});
