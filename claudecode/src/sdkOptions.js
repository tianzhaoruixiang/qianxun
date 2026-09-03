/**
 * Claude Agent SDK 会话选项：干警不用官方编码预设；灵魂只走 systemPrompt。
 * 非干警仍用 claude_code 预设，且故意不设 excludeDynamicSections（只挪位置、不减 token）。
 *
 * user → $HOME/.claude/skills（profile：/opt/data/{userId}/profiles/{profile}/.claude/skills）
 * project → $cwd/.claude/skills（会话工作区）
 */
export const SDK_SETTING_SOURCES = ["user", "project"];

export const OFFICER_SYSTEM_PREFIX =
  "你是调度助手而非编码 CLI。系统提示中的【调度·必读】列出本轮唯一可委派的 agentCode；专业任务必须调用 delegate_to_agent，等返回后汇总。"
  + "禁止用 Glob/Grep 扫描工作区猜 agentCode；禁止用内置 Agent/Task/团队工具或 subagents 日志冒充注册表智能体。\n\n";

export function skillToolEnabled(skillsToolsetOn, enabledSkillNames) {
  return Boolean(skillsToolsetOn)
    && Array.isArray(enabledSkillNames)
    && enabledSkillNames.length > 0;
}

export function buildSystemPrompt({ officer, append, pluginHint } = {}) {
  const extra = `${append || ""}${pluginHint || ""}`;
  if (officer) {
    return OFFICER_SYSTEM_PREFIX + extra;
  }
  return { type: "preset", preset: "claude_code", append: extra };
}
