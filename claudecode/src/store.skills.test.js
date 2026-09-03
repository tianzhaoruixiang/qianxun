import assert from "node:assert/strict";
import { test } from "node:test";
import { skillOptionNames } from "./store.js";

test("skillOptionNames includes directory and yaml name", () => {
  const md = "---\nname: shengcheng-baoxiaodan\ndescription: 报销\n---\n";
  assert.deepEqual(skillOptionNames("shengcheng-baoxiaodan", md), ["shengcheng-baoxiaodan"]);
  const aliased = "---\nname: baoxiao-helper\ndescription: x\n---\n";
  assert.deepEqual(skillOptionNames("shengcheng-baoxiaodan", aliased), [
    "shengcheng-baoxiaodan",
    "baoxiao-helper",
  ]);
});
