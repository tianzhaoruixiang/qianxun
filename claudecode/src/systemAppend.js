export const DOC_HINT = "【工作区】建议将会话产物（xlsx/md/docx 等）写入当前 cwd。"
  + "需要时可读取并执行本智能体 HOME/.claude/skills 下的脚本（用 Bash 运行 python3，不要 Read 脚本后手写替代）。"
  + "尽量避免访问其他用户目录；当前智能体的技能与模板可正常调用。"
  + "沙箱已预装 python3（pandas/numpy/openpyxl/python-docx/matplotlib），不要 pip install。"
  + "不要编造公开下载链接或泄露内部主机名。平台会入库并给出链接。";

/** 人设写入与注入上限（字）。过长会占满首轮上下文。 */
export const SOUL_APPEND_MAX = 4_000;

export function clipSoul(text) {
  const t = String(text || "").trim();
  if (!t) {
    return "";
  }
  return t.length > SOUL_APPEND_MAX ? t.slice(0, SOUL_APPEND_MAX) : t;
}

export function buildSystemAppend(cwd, soul, enabledSkillInfos) {
  const fence = `${DOC_HINT} cwd：${cwd || ""}。`;
  const skills = Array.isArray(enabledSkillInfos) ? enabledSkillInfos : [];
  const names = skills.map((s) => (s && s.name ? String(s.name).trim() : "")).filter(Boolean);
  const skillHint = names.length
    ? `\n【技能】${names.join("、")}。用 Skill 展开后按 SKILL.md 用 Bash 执行 scripts，勿 Read 脚本重写。`
    : "";
  const clipped = clipSoul(soul);
  if (!clipped) {
    return fence + skillHint;
  }
  return `【智能体灵魂】\n${clipped}\n\n${fence}${skillHint}`;
}
