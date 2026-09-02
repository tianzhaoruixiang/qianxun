import assert from "node:assert/strict";
import { test } from "node:test";
import {
  OFFICER_SYSTEM_PREFIX,
  SDK_SETTING_SOURCES,
  buildSystemPrompt,
  skillToolEnabled,
} from "./sdkOptions.js";

test("officer uses short custom prompt not claude_code preset", () => {
  const prompt = buildSystemPrompt({ officer: true, append: "【智能体灵魂】\n短", pluginHint: "" });
  assert.equal(typeof prompt, "string");
  assert.ok(prompt.startsWith(OFFICER_SYSTEM_PREFIX));
  assert.ok(prompt.includes("【智能体灵魂】"));
  assert.equal("excludeDynamicSections" in (typeof prompt === "object" ? prompt : {}), false);
});

test("non-officer keeps claude_code preset without excludeDynamicSections", () => {
  const prompt = buildSystemPrompt({ officer: false, append: "extra", pluginHint: "\n插件" });
  assert.equal(prompt.type, "preset");
  assert.equal(prompt.preset, "claude_code");
  assert.equal(prompt.append, "extra\n插件");
  assert.equal(prompt.excludeDynamicSections, undefined);
});

test("setting sources isolation skips disk CLAUDE.md", () => {
  assert.deepEqual(SDK_SETTING_SOURCES, []);
});

test("skill tool only when toolset on and names exist", () => {
  assert.equal(skillToolEnabled(true, ["brief"]), true);
  assert.equal(skillToolEnabled(true, []), false);
  assert.equal(skillToolEnabled(false, ["brief"]), false);
});
