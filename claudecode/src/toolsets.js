/** 仅 Claude Code 官方工具。不要把 Hermes 工具名混进本目录。 */
export const CATALOG = [
  {
    name: "web",
    label: "Web",
    description: "网页搜索与抓取",
    claudeTools: ["WebSearch", "WebFetch"],
    displayTools: ["WebSearch", "WebFetch"],
  },
  {
    name: "file",
    label: "File",
    description: "读写与搜索工作区文件",
    claudeTools: ["Read", "Write", "Edit", "MultiEdit", "Glob", "Grep", "NotebookEdit", "LSP"],
    displayTools: ["Read", "Write", "Edit", "MultiEdit", "Glob", "Grep", "NotebookEdit", "LSP"],
  },
  {
    name: "terminal",
    label: "Terminal",
    description: "命令行",
    claudeTools: ["Bash", "BashOutput", "PowerShell", "Monitor"],
    displayTools: ["Bash", "BashOutput", "PowerShell", "Monitor"],
  },
  {
    name: "code_execution",
    label: "Code",
    description: "在终端中执行代码",
    claudeTools: ["Bash"],
    displayTools: ["Bash"],
  },
  {
    name: "delegation",
    label: "Delegation",
    description: "子智能体委派",
    claudeTools: ["Agent", "Task", "SendMessage", "ListAgents", "TeamCreate", "TeamDelete", "TaskStop", "TaskOutput"],
    displayTools: ["Agent", "Task", "SendMessage", "ListAgents", "TeamCreate", "TeamDelete", "TaskStop", "TaskOutput"],
  },
  {
    name: "skills",
    label: "Skills",
    description: "技能",
    claudeTools: ["Skill"],
    displayTools: ["Skill"],
  },
  {
    name: "todo",
    label: "Todo",
    description: "任务清单",
    claudeTools: ["TodoWrite", "TaskCreate", "TaskGet", "TaskList", "TaskUpdate"],
    displayTools: ["TodoWrite", "TaskCreate", "TaskGet", "TaskList", "TaskUpdate"],
  },
  {
    name: "kanban",
    label: "Kanban",
    description: "看板",
    claudeTools: ["TodoWrite", "TaskCreate", "TaskList", "TaskUpdate"],
    displayTools: ["TodoWrite", "TaskCreate", "TaskList", "TaskUpdate"],
  },
  {
    name: "plan",
    label: "Plan",
    description: "计划模式与向用户提问",
    claudeTools: ["AskUserQuestion", "ToolSearch", "EnterPlanMode", "ExitPlanMode"],
    displayTools: ["AskUserQuestion", "ToolSearch", "EnterPlanMode", "ExitPlanMode"],
  },
];

/** 缺省打开目录内全部 Claude Code 工具集，避免技能（如报销单）缺 Bash/Write 等跑不起来。 */
export const DEFAULT_ENABLED = CATALOG.map((d) => d.name);

export function isCatalogName(name) {
  const key = String(name || "").trim().toLowerCase();
  return CATALOG.some((d) => d.name === key);
}

/**
 * 只保留 Claude Code 目录中、出现在 enabled 且未 disabled 的项。
 * enabled 为空时回落到 DEFAULT_ENABLED。Hermes / 未知名称丢弃。
 */
export function expandEnabledToolsets(enabled, disabled) {
  const off = lowerSet(disabled);
  const requested = [];
  const seen = new Set();
  const source = Array.isArray(enabled) && enabled.length ? enabled : DEFAULT_ENABLED;
  for (const n of source) {
    const key = String(n || "").trim().toLowerCase();
    if (!key || key === "no_mcp" || off.has(key) || seen.has(key) || !isCatalogName(key)) {
      continue;
    }
    seen.add(key);
    requested.push(key);
  }
  if (requested.length === 0) {
    return DEFAULT_ENABLED.filter((n) => !off.has(n));
  }
  return requested;
}

export function lowerSet(names) {
  const out = new Set();
  for (const n of names || []) {
    if (n && String(n).trim() && String(n).trim().toLowerCase() !== "no_mcp") {
      out.add(String(n).trim().toLowerCase());
    }
  }
  return out;
}

export function catalogClaudeTools() {
  const all = [];
  const seen = new Set();
  for (const d of CATALOG) {
    for (const t of d.claudeTools) {
      if (!seen.has(t)) {
        seen.add(t);
        all.push(t);
      }
    }
  }
  return all;
}

export function allowedClaudeTools(enabledToolsets) {
  let on = lowerSet(enabledToolsets);
  if (on.size === 0) {
    on = lowerSet(DEFAULT_ENABLED);
  }
  const tools = [];
  const seen = new Set();
  for (const d of CATALOG) {
    if (!on.has(d.name)) {
      continue;
    }
    for (const t of d.claudeTools) {
      if (!seen.has(t)) {
        seen.add(t);
        tools.push(t);
      }
    }
  }
  if (tools.length === 0) {
    tools.push("Read");
  }
  return tools;
}

export function applyToolAllowlist(tools, allowlist) {
  const allow = new Set(allowlist || []);
  const next = (tools || []).filter((t) => allow.has(t));
  return next.length ? next : ["Read"];
}

export function disallowedClaudeToolsFromAllowlist(allowed) {
  const allow = new Set(allowed || []);
  return catalogClaudeTools().filter((t) => !allow.has(t));
}

export function disallowedClaudeTools(enabledToolsets) {
  return disallowedClaudeToolsFromAllowlist(allowedClaudeTools(enabledToolsets));
}

export function toInfos(enabled) {
  const on = lowerSet(enabled);
  return CATALOG.map((d) => ({
    name: d.name,
    label: d.label,
    description: d.description,
    platform: "cli",
    platformLabel: "CLI",
    enabled: on.has(d.name),
    configured: true,
    tools: d.displayTools,
  }));
}
