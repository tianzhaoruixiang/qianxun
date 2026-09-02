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
  const list = catalog || "无";
  return `\n【调度】可用：${list}。专业任务只用 delegate_to_agent，等返回后汇总。`;
}
