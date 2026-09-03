import { applyToolAllowlist } from "./toolsets.js";

/** 干警调度时禁用的 Claude 内置委派工具，避免再拉起「墨川小助手」一类分身。 */
export function officerBuiltinDelegationTools() {
  return [
    "Agent",
    "Task",
    "SendMessage",
    "ListAgents",
    "TeamCreate",
    "TeamDelete",
  ];
}

export function officerAllowedTools() {
  return [
    "mcp__qianxun-officer__delegate_to_agent",
    "mcp__qianxun-officer__get_agent_task",
    "mcp__qianxun-officer__cancel_agent_task",
  ];
}

/** 显式 tools 白名单时必须带上 MCP 名，否则 SDK 不会把委派工具交给模型。 */
export function withOfficerMcpTools(tools) {
  const extra = officerAllowedTools();
  const seen = new Set(tools || []);
  const out = Array.isArray(tools) ? [...tools] : [];
  for (const t of extra) {
    if (!seen.has(t)) {
      out.push(t);
      seen.add(t);
    }
  }
  return out;
}

/**
 * 干警本轮允许的 Claude 内置工具。去掉 Bash/LSP/Web/Todo/AskUserQuestion 等大 schema；
 * 无启用技能时不要带 Skill。专业能力走 delegate_to_agent。
 */
export function officerLeanClaudeTools(skillsOn = true) {
  const tools = ["Read", "Write", "Edit", "Glob", "Grep"];
  if (skillsOn) {
    tools.push("Skill");
  }
  return tools;
}

export function applyOfficerLeanTools(tools, { skillsOn } = {}) {
  return applyToolAllowlist(tools, officerLeanClaudeTools(skillsOn === true));
}

export function officerHint(orchestration) {
  const agents = Array.isArray(orchestration?.agents) ? orchestration.agents : [];
  const catalog = agents
    .map((a) => {
      const code = a && a.code ? String(a.code).trim() : "";
      const name = a && a.name ? String(a.name).trim() : code;
      if (!code && !name) {
        return "";
      }
      return name && name !== code ? `${name}（${code}）` : `${code || name}`;
    })
    .filter(Boolean)
    .join("；");
  const list = catalog || "无（注册表没有已启用项）";
  const codes = agents
    .map((a) => (a && a.code ? String(a.code).trim() : ""))
    .filter(Boolean)
    .join("、");
  const codeLine = codes ? `\n- 本轮仅允许 agentCode：${codes}。禁止臆造 search_agent、policy_research 等未列出的 code。` : "";
  return `\n【调度·必读】可委派专业智能体：${list}。${codeLine}
- 专业任务必须直接调用 MCP delegate_to_agent（mcp__qianxun-officer__delegate_to_agent），等返回后汇总；禁止用 Glob/Grep/Read 在工作区或 subagents 目录里“找名单”。
- 工作区 subagents/*.jsonl 是历史 SDK 分身日志，不是注册表；不得据此判断“没有可委派智能体”。
- 仅当 delegate_to_agent 工具报错时，才可说明委派失败；不要因为没找到配置文件就说名单未提供。`;
}
