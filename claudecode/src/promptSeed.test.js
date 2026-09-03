import assert from "node:assert/strict";
import { test } from "node:test";
import { applySeedHistory, isSlashPrompt } from "./promptSeed.js";

test("slash skill prompt is not prefixed with history", () => {
  const r = applySeedHistory("/shengcheng-baoxiaodan 填报销单", "用户：上一轮");
  assert.equal(r.prompt, "/shengcheng-baoxiaodan 填报销单");
  assert.match(r.historyAppend, /上一轮/);
});

test("plain prompt still embeds history", () => {
  const r = applySeedHistory("继续", "用户：你好");
  assert.match(r.prompt, /^【会话历史】/);
  assert.match(r.prompt, /【本轮】\n继续/);
  assert.equal(r.historyAppend, "");
});

test("isSlashPrompt", () => {
  assert.equal(isSlashPrompt("/llm-wiki x"), true);
  assert.equal(isSlashPrompt("  /goal a"), true);
  assert.equal(isSlashPrompt("请用技能"), false);
});
