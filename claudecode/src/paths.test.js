import assert from "node:assert/strict";
import path from "node:path";
import { describe, it } from "node:test";
import {
  DATA_DIR,
  resolveManaged,
  sanitizeSessionId,
  sessionCwd,
  sessionFile,
  workspace,
} from "./paths.js";

describe("sessionCwd", () => {
  it("isolates sessions under workspace/qx", () => {
    assert.equal(sessionCwd("user-01", "sess-a"), `${DATA_DIR}/user-01/workspace/qx/sess-a`);
    assert.notEqual(sessionCwd("user-01", "sess-a"), sessionCwd("user-01", "sess-b"));
  });

  it("falls back to default when session id is unsafe", () => {
    assert.equal(sanitizeSessionId("../etc"), "");
    assert.equal(sessionCwd("user-01", "../x"), `${DATA_DIR}/user-01/workspace/qx/default`);
  });

  it("keeps resume mapping inside the session cwd", () => {
    const cwd = sessionCwd("user-01", "sess-a");
    assert.equal(sessionFile(cwd, "sess-a"), `${cwd}/.qianxun/claude-sessions/sess-a`);
    assert.equal(
      sessionFile(workspace("user-01"), "sess-a"),
      "/opt/data/user-01/workspace/.qianxun/claude-sessions/sess-a".replace("/opt/data", DATA_DIR),
    );
  });
});

describe("resolveManaged", () => {
  it("resolves relative names against the session cwd when provided", () => {
    const resolved = resolveManaged("report.xlsx", "user-01", "sess-a");
    assert.equal(resolved, path.resolve(`${DATA_DIR}/user-01/workspace/qx/sess-a/report.xlsx`));
  });

  it("still allows workspace-prefixed paths under the user root", () => {
    const resolved = resolveManaged("workspace/qx/sess-a/a.xlsx", "user-01");
    assert.equal(resolved, path.resolve(`${DATA_DIR}/user-01/workspace/qx/sess-a/a.xlsx`));
  });
});
