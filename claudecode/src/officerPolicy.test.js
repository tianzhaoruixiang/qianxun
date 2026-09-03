import assert from "node:assert/strict";
import { test } from "node:test";
import {
  applyOfficerLeanTools,
  officerAllowedTools,
  officerHint,
  officerLeanClaudeTools,
  withOfficerMcpTools,
} from "./officerPolicy.js";
import { CATALOG, allowedClaudeTools } from "./toolsets.js";
import { buildSystemAppend } from "./systemAppend.js";
import { isReplaceableOfficerSoul, isStockOfficerSoulV1, isStubSoul } from "./store.js";

test("officer lean tools drop bash web and ask-user", () => {
  const full = allowedClaudeTools(CATALOG.map((d) => d.name));
  const lean = applyOfficerLeanTools(full, { skillsOn: true });
  assert.ok(lean.includes("Read"));
  assert.ok(lean.includes("Skill"));
  assert.ok(!lean.includes("Bash"));
  assert.ok(!lean.includes("WebSearch"));
  assert.ok(!lean.includes("Agent"));
  assert.ok(!lean.includes("AskUserQuestion"));
  assert.deepEqual(applyOfficerLeanTools(full, { skillsOn: false }), officerLeanClaudeTools(false));
});

test("officer tools list includes MCP delegate tools", () => {
  const full = allowedClaudeTools(CATALOG.map((d) => d.name));
  const lean = applyOfficerLeanTools(full, { skillsOn: true });
  const withMcp = withOfficerMcpTools(lean);
  for (const t of officerAllowedTools()) {
    assert.ok(withMcp.includes(t), t);
  }
  assert.ok(!lean.includes("mcp__qianxun-officer__delegate_to_agent"));
});

test("officer hint lists name and code without description", () => {
  const hint = officerHint({
    agents: [
      { code: "legal", name: "法务助手", description: "很长很长的职责说明".repeat(20) },
    ],
  });
  assert.match(hint, /【调度·必读】/);
  assert.match(hint, /法务助手（legal）/);
  assert.match(hint, /仅允许 agentCode：legal/);
  assert.match(hint, /禁止用 Glob\/Grep\/Read/);
  assert.equal(hint.includes("很长很长"), false);
  assert.match(hint, /delegate_to_agent/);
});

test("system append lists skill names only", () => {
  const text = buildSystemAppend("/tmp/ws", "短灵魂", [
    { name: "brief", description: "超长技能说明应当被丢掉" },
  ]);
  assert.match(text, /【技能】brief/);
  assert.equal(text.includes("超长技能说明"), false);
  assert.match(text, /cwd：\/tmp\/ws/);
});

test("stock v1 officer soul is replaceable, customized is not", () => {
  const v1 = `# 数智干警

你是当前登录用户的**专属数智干警**（实例用户：\`u1\`）。
你的产品定位：**说清目标，规划任务，调度专业智能体，汇总结果**。
你不是百科问答框，也不是某一个专业岗位本身。
${"x".repeat(200)}
### 什么时候自己做、什么时候委派
把 Docker 主机名、内部 URL、其他用户路径告诉用户。
${"y".repeat(1200)}`;
  assert.equal(isStockOfficerSoulV1(v1), true);
  assert.equal(isReplaceableOfficerSoul(v1), true);
  assert.equal(isStubSoul(v1), false);
  assert.equal(isReplaceableOfficerSoul("# 数智干警\n我自己改过的人设，不要覆盖。\n" + "自定义".repeat(80)), false);
  assert.equal(isStubSoul(""), true);
});

test("bundled slim soul is not treated as v1 stock", async () => {
  const { readFile } = await import("node:fs/promises");
  const { fileURLToPath } = await import("node:url");
  const { dirname, join } = await import("node:path");
  const file = join(dirname(fileURLToPath(import.meta.url)), "../templates/profiles/default/CLAUDE.md");
  const bundled = await readFile(file, "utf8");
  assert.equal(isStockOfficerSoulV1(bundled), false);
  assert.equal(isStubSoul(bundled), false);
  assert.equal(isReplaceableOfficerSoul(bundled), false);
});
