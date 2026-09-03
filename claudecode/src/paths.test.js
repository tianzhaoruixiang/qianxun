import assert from "node:assert/strict";
import path from "node:path";
import { describe, it } from "node:test";
import {
  DATA_DIR,
  legacySessionCwd,
  resolveManaged,
  sanitizeSessionId,
  sessionCwd,
  sessionFile,
  workspace,
} from "./paths.js";

describe("sessionCwd", () => {
  it("isolates sessions under profile workspace", () => {
    assert.equal(
      sessionCwd("user-01", "sess-a", "baoxiaozhushou"),
      `${DATA_DIR}/user-01/profiles/baoxiaozhushou/workspace/sess-a`,
    );
    assert.notEqual(
      sessionCwd("user-01", "sess-a", "baoxiaozhushou"),
      sessionCwd("user-01", "sess-b", "baoxiaozhushou"),
    );
  });

  it("falls back to default when session id is unsafe", () => {
    assert.equal(sanitizeSessionId("../etc"), "");
    assert.equal(
      sessionCwd("user-01", "../x", "default"),
      `${DATA_DIR}/user-01/profiles/default/workspace/default`,
    );
  });

  it("keeps resume mapping inside the session cwd", () => {
    const cwd = sessionCwd("user-01", "sess-a", "default");
    assert.equal(sessionFile(cwd, "sess-a"), `${cwd}/.qianxun/claude-sessions/sess-a`);
    assert.equal(
      sessionFile(workspace("user-01"), "sess-a"),
      "/opt/data/user-01/workspace/.qianxun/claude-sessions/sess-a".replace("/opt/data", DATA_DIR),
    );
  });

  it("legacySessionCwd keeps old workspace/qx layout", () => {
    assert.equal(
      legacySessionCwd("user-01", "sess-a"),
      `${DATA_DIR}/user-01/workspace/qx/sess-a`,
    );
  });
});

describe("resolveManaged", () => {
  it("resolves relative names against the profile session cwd when provided", () => {
    const resolved = resolveManaged("report.xlsx", "user-01", "sess-a", "baoxiaozhushou");
    assert.equal(
      resolved,
      path.resolve(`${DATA_DIR}/user-01/profiles/baoxiaozhushou/workspace/sess-a/report.xlsx`),
    );
  });

  it("still allows workspace-prefixed paths under the user root", () => {
    const resolved = resolveManaged("workspace/qx/sess-a/a.xlsx", "user-01");
    assert.equal(resolved, path.resolve(`${DATA_DIR}/user-01/workspace/qx/sess-a/a.xlsx`));
  });
});
