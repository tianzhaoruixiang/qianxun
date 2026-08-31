export const DOC_HINT = "【工作区】文件/终端只用当前 cwd 相对路径，勿访问 .. 或其他用户目录。"
  + "沙箱已预装 python3（pandas/numpy/openpyxl/python-docx/matplotlib），不要 pip install。"
  + "生成的 xlsx/md/docx 写入 cwd 普通文件名；不要编造公开下载链接或泄露内部主机名。平台会入库并给出链接。";

const SOUL_APPEND_MAX = 80_000;

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
    ? `\n【技能】${names.join("、")}。需要时用 Skill 工具。`
    : "";
  const clipped = clipSoul(soul);
  if (!clipped) {
    return fence + skillHint;
  }
  return `【智能体灵魂】\n${clipped}\n\n${fence}${skillHint}`;
}
