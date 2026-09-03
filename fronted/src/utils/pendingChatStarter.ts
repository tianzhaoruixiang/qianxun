const STORAGE_KEY = 'qianxun_pending_chat_starter'

export function setPendingChatStarter(agentCode: string, text: string): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ agentCode, text }))
}

/** 读取并清除；仅当 agentCode 匹配且文案非空时返回文案 */
export function consumePendingChatStarter(agentCode: string): string | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    sessionStorage.removeItem(STORAGE_KEY)
    const o = JSON.parse(raw) as { agentCode?: string; text?: string }
    const t = o.text?.trim()
    if (o.agentCode !== agentCode || !t) return null
    return t
  } catch {
    return null
  }
}
