/**
 * Claude Agent SDK 会话选项：干警不用官方编码预设；灵魂只走 systemPrompt，不靠磁盘 CLAUDE.md。
 * 非干警仍用 claude_code 预设，且故意不设 excludeDynamicSections（只挪位置、不减 token）。
 */
export const SDK_SETTING_SOURCES = [];

export const OFFICER_SYSTEM_PREFIX =
  "你是调度助手而非编码 CLI。常识与澄清直接回答；专业任务只用 delegate_to_agent，等返回后汇总。禁止用内置 Agent/Task/团队工具冒充注册表智能体。\n\n";

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
