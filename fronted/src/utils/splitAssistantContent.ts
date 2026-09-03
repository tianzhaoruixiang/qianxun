/** 回答正文中作为附录起点的 Markdown 标题（单独成行，行首最多 3 个空格） */
const APPENDIX_HEADING = /^\s{0,3}#{2,3}[ \t]+(附录|详细说明|appendix)[ \t]*$/i

function fenceOpen(line: string): string | null {
  const m = line.match(/^\s{0,3}(`{3,}|~{3,})/)
  return m ? m[1][0] : null
}

/**
 * 按附录标题把助手回答拆成结论 + 附录。代码围栏内的同名标题不切分。
 */
export function splitAssistantContent(text: string): {
  conclusion: string
  appendix: string
  hasAppendix: boolean
} {
  if (!text) {
    return { conclusion: '', appendix: '', hasAppendix: false }
  }

  const lines = text.split('\n')
  let inFence = false
  let fenceChar = ''

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i]
    const fence = fenceOpen(line)
    if (fence) {
      if (!inFence) {
        inFence = true
        fenceChar = fence
      } else if (fence === fenceChar) {
        inFence = false
        fenceChar = ''
      }
      continue
    }
    if (inFence) continue
    if (!APPENDIX_HEADING.test(line)) continue

    const conclusion = lines.slice(0, i).join('\n').replace(/\s+$/, '')
    const appendix = lines.slice(i + 1).join('\n').replace(/^\n/, '')
    return { conclusion, appendix, hasAppendix: true }
  }

  return { conclusion: text, appendix: '', hasAppendix: false }
}
