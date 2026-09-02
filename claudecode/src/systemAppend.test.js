import assert from "node:assert/strict";
import { test } from "node:test";
import { SOUL_APPEND_MAX, clipSoul } from "./systemAppend.js";

test("clipSoul caps at 4000 characters", () => {
  assert.equal(SOUL_APPEND_MAX, 4000);
  const long = "字".repeat(5000);
  assert.equal(clipSoul(long).length, 4000);
});
