/** 修正模型常见的表格写法，避免 GFM 表格解析失败 */
export function normalizeMarkdown(source: string): string {
  if (!source) return ''
  const text = source.replace(/\r\n/g, '\n').replace(/｜/g, '|')
  const lines = text.split('\n')
  const out: string[] = []

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i]
    const next = lines[i + 1]
    const prev = i > 0 ? lines[i - 1] : ''
    // 只在「表头后缺分隔行」时补一行；不能插在任意两行数据之间
    const startsTable = !prev.trim() || (!isTableRow(prev) && !isSeparatorRow(prev))
    if (
      startsTable &&
      isTableRow(line) &&
      next != null &&
      !isSeparatorRow(next) &&
      isTableRow(next)
    ) {
      const cols = splitRow(line).length
      if (cols >= 2) {
        out.push(line)
        out.push(buildSeparator(cols))
        continue
      }
    }
    out.push(line)
  }
  return out.join('\n')
}

function isTableRow(line: string): boolean {
  const t = line.trim()
  if (!t.includes('|')) return false
  if (isSeparatorRow(t)) return false
  return t.startsWith('|') || t.endsWith('|') || (t.match(/\|/g) || []).length >= 2
}

function isSeparatorRow(line: string): boolean {
  const t = line.trim()
  if (!t.includes('|') && !t.includes('-')) return false
  return /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(t)
}

function splitRow(line: string): string[] {
  let t = line.trim()
  if (t.startsWith('|')) t = t.slice(1)
  if (t.endsWith('|')) t = t.slice(0, -1)
  return t.split('|').map((c) => c.trim())
}

function buildSeparator(cols: number): string {
  return `| ${Array.from({ length: cols }, () => '---').join(' | ')} |`
}
