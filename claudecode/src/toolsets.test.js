import assert from "node:assert/strict";
import { test } from "node:test";
import { CATALOG, DEFAULT_ENABLED, allowedClaudeTools, expandEnabledToolsets } from "./toolsets.js";

test("default enabled is file and skills only", () => {
  assert.deepEqual(DEFAULT_ENABLED, ["file", "skills"]);
  const tools = allowedClaudeTools(DEFAULT_ENABLED);
  assert.ok(tools.includes("Read"));
  assert.ok(tools.includes("Skill"));
  assert.ok(!tools.includes("Bash"));
  assert.ok(!tools.includes("WebSearch"));
  assert.ok(!tools.includes("Agent"));
});

test("expand respects enabled list and disabled", () => {
  assert.deepEqual(expandEnabledToolsets([], []), ["file", "skills"]);
  assert.deepEqual(expandEnabledToolsets(["web", "file", "browser"], ["web"]), ["file"]);
  assert.deepEqual(
    expandEnabledToolsets(CATALOG.map((d) => d.name), []),
    CATALOG.map((d) => d.name),
  );
});
