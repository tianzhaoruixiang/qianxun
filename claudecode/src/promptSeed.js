/**
 * 官方 SDK：以 {@code /} 开头的 prompt 才会按命令/技能派发。
 * 历史不能拼到斜杠前面，否则变成普通文本，Skill 不会展开。
 */
export function applySeedHistory(prompt, historyText) {
  const p = prompt == null ? "" : String(prompt);
  const hist = String(historyText || "").trim();
  if (!hist) {
    return { prompt: p, historyAppend: "" };
  }
  if (p.trim().startsWith("/")) {
    return { prompt: p, historyAppend: `\n【会话历史】\n${hist}` };
  }
  return { prompt: `【会话历史】\n${hist}\n【本轮】\n${p}`, historyAppend: "" };
}

export function isSlashPrompt(prompt) {
  return String(prompt || "").trim().startsWith("/");
}
