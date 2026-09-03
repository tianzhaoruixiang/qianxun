import request from '@/utils/request'
import type { ContextUsage, ToolCallInfo } from '@/types/chat'
import { buildAuthHeaders } from '@/utils/apiHeaders'
import { sanitizeUserFacingText } from '@/utils/userFacingCopy'
import type { SessionGoal } from '@/utils/sessionGoal'

export interface CreateSessionResponse {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  agentCode?: string
  hermesProfile?: string
  agentName?: string
  goal?: SessionGoal | null
}

export interface CreateSessionParams {
  title?: string
  agentCode?: string
  hermesProfile?: string
  agentName?: string
}

export interface ActiveRunInfo {
  runId: string
  traceId?: string | null
  sessionId: string
  status: string
  assistantMessageId?: string | null
  lastSeq: number
  /** 服务端已收到停止请求，但 Run 可能尚未终态 */
  cancelRequested?: boolean | null
  hermesProfile?: string | null
  agentCode?: string | null
  modelCode?: string | null
  startedAtMs?: number
  toolCallCount?: number
  delegationCount?: number
}

export interface StreamEventHandlers {
  signal?: AbortSignal
  onStarted?: (payload: { sessionId?: string; runId?: string; assistantMessageId?: string; seq?: number }) => void
  onToken?: (text: string, meta?: { seq?: number; runId?: string; assistantMessageId?: string }) => void
  onToolCall?: (toolCall: ToolCallInfo, meta?: { seq?: number }) => void
  onUsage?: (usage: ContextUsage, meta?: { seq?: number }) => void
  onCompact?: (event: { phase: string; trigger?: string; preTokens?: number }) => void
  onSuggestions?: (items: string[], meta?: { seq?: number }) => void
  onStreamWarning?: (message: string) => void
  onGeneratedFile?: (file: { id?: string; name?: string; publicToken?: string; href?: string }) => void
  onDone?: (payload: {
    assistantMessageId?: string
    sessionId?: string
    cancelled?: boolean
    seq?: number
    runId?: string
  }) => void
  onError?: (message: string) => void
  onSessionGoal?: (payload: { cleared?: boolean; goal?: SessionGoal | Record<string, string> }) => void
  onDelegationUpdate?: (payload: Record<string, unknown>, meta?: { seq?: number }) => void
  /** 每个事件的序号回调（用于续传 afterSeq） */
  onSeq?: (seq: number) => void
}

export interface StreamChatParams extends StreamEventHandlers {
  sessionId: string
  content: string
  modelCode?: string
  agentCode?: string
  hermesProfile?: string
  fileIds?: string[]
  skillName?: string
  goal?: SessionGoal
  clearGoal?: boolean
  agentsStatus?: boolean
  slashCommand?: string
}

export interface SubscribeChatParams extends StreamEventHandlers {
  sessionId: string
  afterSeq?: number
  /** 未收到 done 时是否按致命错误抛出；再附着默认 true */
  requireDone?: boolean
}

/**
 * 创建会话
 */
export function createSession(title?: string, extra?: Omit<CreateSessionParams, 'title'>): Promise<CreateSessionResponse> {
  return request.post('/sessions/create', {
    jsonArg: {
      title: title || '新对话',
      agentCode: extra?.agentCode,
      hermesProfile: extra?.hermesProfile,
      agentName: extra?.agentName,
    },
  })
}

export function getActiveRun(sessionId: string): Promise<ActiveRunInfo | null> {
  const baseURL = resolveApiBaseUrl()
  return fetch(
    `${baseURL}/sessions/${encodeURIComponent(sessionId)}/chat/runs/active`,
    { headers: buildAuthHeaders(), method: 'GET' },
  ).then(async (response) => {
    if (response.status === 204) return null
    if (response.status === 401) {
      localStorage.removeItem('token')
      const base = import.meta.env.BASE_URL.replace(/\/?$/, '/')
      window.location.assign(`${base}login`)
      return null
    }
    if (!response.ok) {
      const detail = await readHttpErrorMessage(response)
      throw new Error(sanitizeUserFacingText(`查询进行中输出失败（${response.status}）：${detail}`))
    }
    const json = await response.json() as { data?: ActiveRunInfo; code?: number }
    return json?.data ?? null
  })
}

export function stopChatStream(sessionId: string): Promise<void> {
  return request.post(`/sessions/${encodeURIComponent(sessionId)}/chat/stream/stop`, {})
}

function resolveApiBaseUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL || '/QianXunService'
  return base.endsWith('/') ? base.slice(0, -1) : base
}

function getStreamHeaders(): Record<string, string> {
  return {
    ...buildAuthHeaders(),
    'Content-Type': 'application/json',
    // 同时接受 JSON：冲突/鉴权失败时后端返回 JSON 正文，避免空 409
    Accept: 'text/event-stream, application/json',
  }
}

async function readHttpErrorMessage(response: Response): Promise<string> {
  try {
    const text = await response.clone().text()
    if (!text) return `HTTP ${response.status}`
    try {
      const json = JSON.parse(text) as { message?: string; data?: unknown }
      if (json?.message) return json.message
    } catch {
      /* 非 JSON */
    }
    return text.slice(0, 300)
  } catch {
    return `HTTP ${response.status}`
  }
}

function parseSseBlock(block: string): { event: string; data: string } | null {
  const lines = block.replace(/\r/g, '').split('\n')
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (!line) continue
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }

  if (!dataLines.length) return null
  return { event: eventName, data: dataLines.join('\n') }
}

function parseMaybeJson<T>(value: string): T | null {
  try {
    return JSON.parse(value) as T
  } catch {
    return null
  }
}

function readSeq(payload: Record<string, unknown> | null | undefined): number | undefined {
  if (!payload) return undefined
  const raw = payload.seq
  return typeof raw === 'number' && Number.isFinite(raw) ? raw : undefined
}

async function consumeSseStream(
  response: Response,
  params: StreamEventHandlers & { requireDone?: boolean },
): Promise<void> {
  if (!response.body) {
    throw new Error('聊天请求失败: 响应流为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let streamFailed = false
  let sawDone = false
  const requireDone = params.requireDone !== false

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) {
        buffer += decoder.decode()
      } else {
        buffer += decoder.decode(value, { stream: true })
      }
      buffer = buffer.replace(/\r\n/g, '\n')

      let sepIndex = buffer.indexOf('\n\n')
      while (sepIndex >= 0) {
        const block = buffer.slice(0, sepIndex)
        buffer = buffer.slice(sepIndex + 2)
        const event = parseSseBlock(block)
        if (!event) {
          sepIndex = buffer.indexOf('\n\n')
          continue
        }

        if (event.event === 'heartbeat') {
          sepIndex = buffer.indexOf('\n\n')
          continue
        }

        if (event.event === 'started') {
          const payload = parseMaybeJson<Record<string, unknown>>(event.data)
          const seq = readSeq(payload)
          if (seq != null) params.onSeq?.(seq)
          params.onStarted?.({
            sessionId: typeof payload?.sessionId === 'string' ? payload.sessionId : undefined,
            runId: typeof payload?.runId === 'string' ? payload.runId : undefined,
            assistantMessageId: typeof payload?.assistantMessageId === 'string' ? payload.assistantMessageId : undefined,
            seq,
          })
        } else if (event.event === 'token') {
          const token = parseMaybeJson<Record<string, unknown>>(event.data)
          const seq = readSeq(token)
          if (seq != null) params.onSeq?.(seq)
          const text = typeof token?.text === 'string' ? token.text : ''
          if (text) {
            params.onToken?.(text, {
              seq,
              runId: typeof token?.runId === 'string' ? token.runId : undefined,
              assistantMessageId: typeof token?.assistantMessageId === 'string' ? token.assistantMessageId : undefined,
            })
          }
        } else if (event.event === 'tool_call') {
          const payload = parseMaybeJson<ToolCallInfo & { seq?: number }>(event.data)
          if (payload) {
            const seq = typeof payload.seq === 'number' ? payload.seq : undefined
            if (seq != null) params.onSeq?.(seq)
            params.onToolCall?.(payload, { seq })
          }
        } else if (event.event === 'delegation_update') {
          const payload = parseMaybeJson<Record<string, unknown>>(event.data)
          if (payload) {
            const seq = typeof payload.seq === 'number' ? payload.seq : undefined
            if (seq != null) params.onSeq?.(seq)
            params.onDelegationUpdate?.(payload, { seq })
          }
        } else if (event.event === 'usage') {
          const payload = parseMaybeJson<ContextUsage & { seq?: number }>(event.data)
          if (payload) {
            const seq = typeof payload.seq === 'number' ? payload.seq : undefined
            if (seq != null) params.onSeq?.(seq)
            params.onUsage?.(payload, { seq })
          }
        } else if (event.event === 'compact') {
          const payload = parseMaybeJson<{ phase?: string; trigger?: string; preTokens?: number; seq?: number }>(event.data)
          if (payload?.phase) {
            if (typeof payload.seq === 'number') params.onSeq?.(payload.seq)
            params.onCompact?.({
              phase: payload.phase,
              trigger: typeof payload.trigger === 'string' ? payload.trigger : '',
              preTokens: typeof payload.preTokens === 'number' ? payload.preTokens : undefined,
            })
          }
        } else if (event.event === 'suggestions') {
          const payload = parseMaybeJson<{ items?: string[]; seq?: number }>(event.data)
          if (Array.isArray(payload?.items) && payload.items.length) {
            if (typeof payload.seq === 'number') params.onSeq?.(payload.seq)
            params.onSuggestions?.(payload.items.filter((x) => typeof x === 'string' && x.trim()), { seq: payload.seq })
          }
        } else if (event.event === 'generated_file') {
          const payload = parseMaybeJson<{
            id?: string
            name?: string
            publicToken?: string
            href?: string
            seq?: number
          }>(event.data)
          if (payload) {
            if (typeof payload.seq === 'number') params.onSeq?.(payload.seq)
            params.onGeneratedFile?.(payload)
          }
        } else if (event.event === 'stream_warning') {
          const payload = parseMaybeJson<{ message?: string }>(event.data)
          const msg = sanitizeUserFacingText(payload?.message || '上游流式警告')
          params.onStreamWarning?.(msg)
        } else if (event.event === 'error') {
          streamFailed = true
          const payload = parseMaybeJson<{ message?: string }>(event.data)
          const message = sanitizeUserFacingText(payload?.message || '流式对话异常')
          params.onError?.(message)
          throw new Error(message)
        } else if (event.event === 'session_goal') {
          const payload = parseMaybeJson<{ cleared?: boolean; goal?: SessionGoal }>(event.data)
          if (payload) params.onSessionGoal?.(payload)
        } else if (event.event === 'done') {
          sawDone = true
          const payload = parseMaybeJson<{
            assistantMessageId?: string
            sessionId?: string
            cancelled?: boolean
            seq?: number
            runId?: string
          }>(event.data)
          if (typeof payload?.seq === 'number') params.onSeq?.(payload.seq)
          params.onDone?.(payload || {})
        }

        sepIndex = buffer.indexOf('\n\n')
      }

      if (done) break
    }
  } finally {
    try {
      reader.releaseLock()
    } catch {
      /* already released */
    }
  }

  if (requireDone && !sawDone && !streamFailed && !params.signal?.aborted) {
    const msg = sanitizeUserFacingText('对话流在未收到完成信号前结束，请重试')
    params.onError?.(msg)
    throw new Error(msg)
  }
}

/**
 * 调用后端流式聊天接口（POST + SSE）：创建 Run 并订阅
 */
export async function streamChat(params: StreamChatParams): Promise<void> {
  const baseURL = resolveApiBaseUrl()

  const response = await fetch(
    `${baseURL}/sessions/${encodeURIComponent(params.sessionId)}/chat/stream`,
    {
      method: 'POST',
      headers: getStreamHeaders(),
      body: JSON.stringify({
        content: params.content,
        modelCode: params.modelCode,
        agentCode: params.agentCode,
        hermesProfile: params.hermesProfile,
        fileIds: params.fileIds,
        skillName: params.skillName,
        goal: params.goal,
        clearGoal: params.clearGoal,
        agentsStatus: params.agentsStatus,
        slashCommand: params.slashCommand,
      }),
      signal: params.signal,
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem('token')
      const base = import.meta.env.BASE_URL.replace(/\/?$/, '/')
      window.location.assign(`${base}login`)
    }
    const detail = await readHttpErrorMessage(response)
    if (response.status === 409) {
      const err = new Error(sanitizeUserFacingText(
        detail && !/^HTTP\s*409$/i.test(detail)
          ? detail
          : '该会话正在输出中，请等待完成或先停止后再发送',
      ))
      err.name = 'ChatConflictError'
      throw err
    }
    throw new Error(sanitizeUserFacingText(`聊天请求失败（${response.status}）：${detail}`))
  }

  await consumeSseStream(response, { ...params, requireDone: true })
}

/**
 * 再附着进行中的会话输出（GET + SSE）
 */
export async function subscribeChatStream(params: SubscribeChatParams): Promise<void> {
  const baseURL = resolveApiBaseUrl()
  const afterSeq = params.afterSeq ?? 0
  const url = `${baseURL}/sessions/${encodeURIComponent(params.sessionId)}/chat/stream/subscribe?afterSeq=${encodeURIComponent(String(afterSeq))}`

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      ...buildAuthHeaders(),
      Accept: 'text/event-stream',
    },
    signal: params.signal,
  })

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem('token')
      const base = import.meta.env.BASE_URL.replace(/\/?$/, '/')
      window.location.assign(`${base}login`)
    }
    if (response.status === 404) {
      return
    }
    const detail = await readHttpErrorMessage(response)
    throw new Error(sanitizeUserFacingText(`续传输出失败（${response.status}）：${detail}`))
  }

  await consumeSseStream(response, { ...params, requireDone: params.requireDone !== false })
}
