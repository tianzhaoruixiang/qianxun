/** 从近期会话正文提取可点击的下一步建议（后端未返回时兜底） */

export type SuggestionMessage = {
  role: string
  content?: string
}

/**
 * 优先从最近助手回答与用户问题中抽取问句/可执行短句；不足再补与主题相关的追问，避免空泛套话。
 */
export function fallbackNextStepSuggestions(
  content: string,
  recentMessages: SuggestionMessage[] = [],
): string[] {
  const unique: string[] = []
  const push = (raw: string) => {
    const item = (raw || '').replace(/\s+/g, ' ').trim()
    if (!item || item.length < 4 || item.length > 40) return
    if (unique.includes(item)) return
    unique.push(item)
  }

  const assistantText = (content || '').replace(/```[\s\S]*?```/g, '').trim()
  for (const m of assistantText.matchAll(/[^\n。！!]{6,36}[？?]/g)) {
    push(m[0])
    if (unique.length >= 3) return unique.slice(0, 3)
  }

  const recentUser = [...recentMessages]
    .reverse()
    .find((m) => m.role === 'user' && (m.content || '').trim())
  const topic = summarizeTopic(recentUser?.content || assistantText)

  const themed = topic
    ? [
        `结合「${topic}」给出可执行步骤`,
        `针对「${topic}」补充关键依据或示例`,
        `「${topic}」还有哪些边界或注意点？`,
      ]
    : [
        '请按优先级列出可执行步骤',
        '请补充关键依据或具体示例',
        '还有哪些边界条件或注意点？',
      ]

  for (const extra of themed) {
    push(extra)
    if (unique.length >= 3) break
  }
  return unique.slice(0, 3)
}

function summarizeTopic(text: string): string {
  const t = (text || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!t) return ''
  // 去掉常见前缀，截成短主题
  const cleaned = t
    .replace(/^(请|帮我|麻烦|我想|我要|如何|怎么|怎样)/, '')
    .trim()
  const slice = (cleaned || t).slice(0, 12)
  return slice.length >= 4 ? slice : ''
}
