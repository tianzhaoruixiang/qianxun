/**
 * 写入 Mem0 前的简单脱敏（密钥 / Token / 常见隐私字段）。
 */

const PATTERNS = [
  // OpenAI / Anthropic / generic API keys
  /\b(sk-[A-Za-z0-9_-]{16,})\b/g,
  /\b(sk-ant-[A-Za-z0-9_-]{16,})\b/g,
  /\b(m0-[A-Za-z0-9_-]{16,})\b/g,
  // Bearer / Authorization tokens
  /\b(Bearer\s+)[A-Za-z0-9._\-+=/]{12,}/gi,
  /\b(Authorization:\s*)\S+/gi,
  // JWT-ish
  /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9._\-+=/]{10,}\b/g,
  // AWS-ish access keys
  /\b(AKIA[0-9A-Z]{16})\b/g,
  // Emails (粗粒度)
  /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi,
  // 中国大陆手机号
  /\b1[3-9]\d{9}\b/g,
];

/**
 * @param {string} text
 * @returns {string}
 */
export function redactSecrets(text) {
  let out = String(text ?? "");
  for (const re of PATTERNS) {
    out = out.replace(re, (match, g1) => {
      if (typeof g1 === "string" && match.toLowerCase().startsWith("bearer")) {
        return `${g1}[REDACTED]`;
      }
      if (typeof g1 === "string" && match.toLowerCase().startsWith("authorization")) {
        return `${g1}[REDACTED]`;
      }
      return "[REDACTED]";
    });
  }
  return out;
}
