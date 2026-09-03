import request from '@/utils/request'

export interface ChatSessionApi {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messageCount?: number | null
  lastMessagePreview?: string | null
  agentCode?: string | null
  hermesProfile?: string | null
  agentName?: string | null
  goal?: {
    title?: string
    description?: string
    steps?: string
    constraints?: string
    stopAfterTurns?: number | null
  } | null
  /** 服务端标记：该会话是否有进行中的流式生成 */
  streaming?: boolean | null
}

export interface ChatMessageApi {
  id: string
  sessionId: string
  role: string
  content: string
  toolCalls?: string | unknown[] | null
  usage?: string | Record<string, unknown> | null
  suggestions?: string | string[] | null
  createdAt: string
  status?: string | null
  runId?: string | null
}

export interface ChatSessionListApi {
  items: ChatSessionApi[]
  page: number
  limit: number
  offset: number
  hasMore: boolean
  agentFacets?: Array<{ groupKey: string; label: string }>
}

export const HISTORY_PAGE_SIZE = 20

export function listSessions(params?: {
  page?: number
  limit?: number
  offset?: number
  keyword?: string
  agentGroup?: string
  cursorUpdatedAt?: string
  cursorId?: string
}): Promise<ChatSessionListApi> {
  return request.post('/sessions/list', {
    jsonArg: {
      page: params?.page,
      limit: params?.limit ?? HISTORY_PAGE_SIZE,
      offset: params?.offset,
      keyword: params?.keyword,
      agentGroup: params?.agentGroup,
      cursorUpdatedAt: params?.cursorUpdatedAt,
      cursorId: params?.cursorId,
    },
  })
}

/** 获取单个会话（无 messageCount / 预览字段） */
export function getSession(id: string): Promise<ChatSessionApi> {
  return request.post('/sessions/get', { jsonArg: { id } })
}

export function updateSessionTitle(id: string, title: string): Promise<ChatSessionApi> {
  return request.post('/sessions/update', { jsonArg: { id, title } })
}

export function updateSessionGoal(
  id: string,
  payload: { goal?: ChatSessionApi['goal']; clearGoal?: boolean },
): Promise<ChatSessionApi> {
  return request.post('/sessions/update', {
    jsonArg: {
      id,
      goal: payload.goal,
      clearGoal: payload.clearGoal,
    },
  })
}

export function deleteSession(id: string): Promise<void> {
  return request.post('/sessions/delete', { jsonArg: { id } })
}

export function listSessionMessages(sessionId: string): Promise<ChatMessageApi[]> {
  return request.post('/sessions/messages', { jsonArg: { sessionId } })
}
