import type { ChatMessageApi } from '@/api/sessions'
import type { ContextUsage, Message, MessageRole, ToolCallInfo } from '@/types/chat'

export function apiMessageToMessage(m: ChatMessageApi): Message {
  const status = (m.status || 'completed').trim() || 'completed'
  const live = status === 'streaming'
  return {
    id: m.id,
    role: m.role as MessageRole,
    content: m.content || '',
    toolCalls: normalizeLoadedToolCalls(parseToolCallsField(m.toolCalls), live),
    toolCalling: live,
    usage: parseUsageField(m.usage),
    suggestions: parseSuggestionsField(m.suggestions),
    timestamp: new Date(m.createdAt).getTime(),
    status,
    runId: m.runId || undefined,
  }
}

function parseToolCallsField(raw: ChatMessageApi['toolCalls']): ToolCallInfo[] {
  if (raw == null || raw === '') return []
  if (Array.isArray(raw)) return raw as ToolCallInfo[]
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw) as unknown
      return Array.isArray(parsed) ? (parsed as ToolCallInfo[]) : []
    } catch {
      return []
    }
  }
  return []
}

function normalizeLoadedToolCalls(tools: ToolCallInfo[], live: boolean): ToolCallInfo[] {
  if (live || !tools.length) return tools
  return tools.map((t) => {
    const s = (t.status || '').toLowerCase()
    if (s === 'completed' || s === 'error' || s === 'awaiting' || s === 'background') return t
    return { ...t, status: 'completed' }
  })
}

function parseUsageField(raw: ChatMessageApi['usage']): ContextUsage | undefined {
  if (raw == null || raw === '') return undefined
  if (typeof raw === 'object' && !Array.isArray(raw)) return raw as ContextUsage
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw) as unknown
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as ContextUsage
      }
    } catch {
      /* ignore */
    }
  }
  return undefined
}

function parseSuggestionsField(raw: ChatMessageApi['suggestions']): string[] | undefined {
  if (raw == null || raw === '') return undefined
  if (Array.isArray(raw)) {
    const items = raw.filter((x): x is string => typeof x === 'string' && x.trim().length > 0)
    return items.length ? items : undefined
  }
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw) as unknown
      if (Array.isArray(parsed)) {
        const items = parsed.filter((x): x is string => typeof x === 'string' && x.trim().length > 0)
        return items.length ? items : undefined
      }
    } catch {
      /* ignore */
    }
  }
  return undefined
}
