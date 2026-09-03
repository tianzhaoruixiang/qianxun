import assert from "node:assert/strict";
import { test } from "node:test";
import { CATALOG, DEFAULT_ENABLED, allowedClaudeTools, expandEnabledToolsets, toInfos } from "./toolsets.js";

test("default enabled is the full Claude Code catalog", () => {
  assert.deepEqual(DEFAULT_ENABLED, CATALOG.map((d) => d.name));
  const tools = allowedClaudeTools(DEFAULT_ENABLED);
  assert.ok(tools.includes("Read"));
  assert.ok(tools.includes("Skill"));
  assert.ok(tools.includes("Bash"));
  assert.ok(tools.includes("WebSearch"));
  assert.ok(tools.includes("Agent"));
});

test("expand respects enabled list and disabled", () => {
  assert.deepEqual(expandEnabledToolsets([], []), CATALOG.map((d) => d.name));
  assert.deepEqual(expandEnabledToolsets(["web", "file", "browser"], ["web"]), ["file"]);
  assert.deepEqual(
    expandEnabledToolsets(CATALOG.map((d) => d.name), []),
    CATALOG.map((d) => d.name),
  );
  assert.deepEqual(
    expandEnabledToolsets([], CATALOG.map((d) => d.name)),
    [],
  );
});

test("toInfos treats empty enabled as all disabled", () => {
  const infos = toInfos([]);
  assert.equal(infos.length, CATALOG.length);
  assert.ok(infos.every((t) => t.enabled === false));
});

test("subset enabled is not expanded to full catalog", () => {
  assert.deepEqual(expandEnabledToolsets(["file", "skills"], []), ["file", "skills"]);
});
