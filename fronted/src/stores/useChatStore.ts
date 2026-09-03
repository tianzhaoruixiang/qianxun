import { defineStore } from 'pinia'
import { computed, nextTick, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  createSession,
  getActiveRun,
  stopChatStream,
  streamChat,
  subscribeChatStream,
} from '@/api/chat'
import router from '@/router'
import {
  deleteSession as apiDeleteSession,
  HISTORY_PAGE_SIZE,
  listSessionMessages,
  listSessions,
  updateSessionTitle,
  type ChatSessionApi,
} from '@/api/sessions'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useKnowledgeStore } from '@/stores/useKnowledgeStore'
import type { AgentRegistryItem } from '@/api/registry'
import type { ContextUsage, HistorySession, Message, ToolCallInfo } from '@/types/chat'
import { useDataFilesStore } from '@/stores/useDataFilesStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import {
  agentGroupKey,
  DIGITAL_OFFICER_KEY,
  displayAgentName,
} from '@/utils/agentDisplay'
import { getSystemName } from '@/utils/systemName'
import { accumulateSessionUsage, formatSessionDuration, formatTokenCount, formatUsdCost, sessionActiveDurationMs } from '@/utils/contextUsage'
import { apiMessageToMessage } from '@/utils/mapApiMessage'
import { type SessionGoal } from '@/utils/sessionGoal'
import { isSubagentTool } from '@/utils/subagentTools'

function generateId(): string {
  return `msg_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
}

function isAbortError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === 'AbortError') return true
  if (error instanceof Error && error.name === 'AbortError') return true
  return false
}

function isChatConflictError(error: unknown): boolean {
  return error instanceof Error && error.name === 'ChatConflictError'
}

function isActiveRunRunning(info: { status?: string; cancelRequested?: boolean | null } | null | undefined): boolean {
  if (info?.cancelRequested) return false
  return String(info?.status || '').toUpperCase() === 'RUNNING'
}

/** 同时间戳时保证 user 在 assistant 前，避免问答颠倒 */
function sortMessagesChronologically(list: Message[]): Message[] {
  return [...list].sort((a, b) => {
    if (a.timestamp !== b.timestamp) return a.timestamp - b.timestamp
    const roleRank = (role: string) => (role === 'user' ? 0 : role === 'assistant' ? 1 : 2)
    const byRole = roleRank(a.role) - roleRank(b.role)
    if (byRole !== 0) return byRole
    return String(a.id).localeCompare(String(b.id))
  })
}

function toHistorySession(s: ChatSessionApi, agents: { code: string; name: string; hermesProfile?: string }[] = []): HistorySession {
  const created = new Date(s.createdAt).getTime()
  const updatedRaw = s.updatedAt ? new Date(s.updatedAt).getTime() : created
  const agentName = displayAgentName({
    agentCode: s.agentCode,
    hermesProfile: s.hermesProfile,
    agentName: s.agentName,
    agents,
  })
  return {
    id: s.id,
    title: s.title,
    createdAt: created,
    updatedAt: Number.isFinite(updatedRaw) ? updatedRaw : created,
    messageCount: s.messageCount ?? 0,
    lastMessage: s.lastMessagePreview || '',
    messages: [],
    agentCode: s.agentCode?.trim() || undefined,
    hermesProfile: s.hermesProfile?.trim() || undefined,
    agentName,
    goal: normalizeGoal(s.goal),
    streaming: Boolean(s.streaming),
  }
}

function normalizeGoal(raw?: ChatSessionApi['goal'] | SessionGoal | null): SessionGoal | null {
  if (!raw) return null
  const title = (raw.title || '').trim()
  const description = (raw.description || '').trim()
  const steps = (raw.steps || '').trim()
  const constraints = (raw.constraints || '').trim()
  const turnsRaw = Number((raw as SessionGoal).stopAfterTurns)
  const stopAfterTurns = Number.isFinite(turnsRaw) && turnsRaw > 0 ? Math.round(turnsRaw) : null
  if (!title && !description) return null
  return { title, description, steps, constraints, stopAfterTurns }
}

function tryParseArgsObject(raw?: string): Record<string, unknown> | null {
  if (!raw?.trim()) return null
  try {
    const parsed = JSON.parse(raw) as unknown
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
  } catch {
    /* ignore */
  }
  return null
}

function mergeArgs(prev: string | undefined, incoming: string | undefined): string | undefined {
  if (incoming == null || incoming === '') return prev
  const trimmed = incoming.trim()
  if (!prev) return incoming
  const incomingObj = tryParseArgsObject(trimmed)
  const prevObj = tryParseArgsObject(prev)
  if (incomingObj && prevObj) {
    if (Object.keys(incomingObj).length === 0) return prev
    return JSON.stringify({ ...prevObj, ...incomingObj })
  }
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) return incoming
  return `${prev}${incoming}`
}

function isTerminalStatus(status?: string): boolean {
  const s = (status || '').toLowerCase()
  return s === 'completed' || s === 'error'
}

function isAwaitingStatus(status?: string): boolean {
  const s = (status || '').toLowerCase()
  return s === 'awaiting' || s === 'background'
}

function closeOpenTools(tools: ToolCallInfo[] | undefined, terminal: 'completed' | 'error'): ToolCallInfo[] {
  const now = Date.now()
  const list = tools ?? []
  const awaitingIds = new Set(
    list.filter((t) => isAwaitingStatus(t.status) && t.toolCallId).map((t) => t.toolCallId!.trim()),
  )
  return list.map((t) => {
    if (isTerminalStatus(t.status) || isAwaitingStatus(t.status)) return t
    let pid = t.parentId?.trim()
    let underAwaiting = false
    const seen = new Set<string>()
    while (pid && !seen.has(pid)) {
      seen.add(pid)
      if (awaitingIds.has(pid)) {
        underAwaiting = true
        break
      }
      const parent = list.find((x) => x.toolCallId?.trim() === pid)
      pid = parent?.parentId?.trim()
    }
    if (underAwaiting) return t
    return {
      ...t,
      status: terminal,
      endedAt: t.endedAt || now,
      durationMs: t.durationMs ?? (t.startedAt ? Math.max(0, now - t.startedAt) : t.durationMs),
    }
  })
}

function mergeToolStatus(cur?: string, incoming?: string, reopen = false): string {
  const i = (incoming || '').toLowerCase()
  const c = (cur || '').toLowerCase()
  if (!i) return cur || 'running'
  if (isAwaitingStatus(i)) return 'awaiting'
  if (i === 'error') return 'error'
  if (i === 'completed') {
    if (reopen && (c === 'running' || c === 'started' || isAwaitingStatus(c))) {
      return i
    }
    return 'completed'
  }
  if (isAwaitingStatus(c) && (i === 'running' || i === 'started')) return 'awaiting'
  if (isTerminalStatus(c) && !isTerminalStatus(i)) {
    if (reopen && (i === 'running' || i === 'started' || isAwaitingStatus(i))) return incoming || 'running'
    return c
  }
  return incoming || cur || 'running'
}

function sameToolName(a?: string, b?: string): boolean {
  return !!(a && b && a.trim() === b.trim())
}

function findLastOpenTool(list: ToolCallInfo[], toolName: string, parentId?: string): number {
  const pid = parentId?.trim() || ''
  for (let i = list.length - 1; i >= 0; i -= 1) {
    const row = list[i]
    if (!sameToolName(row.toolName, toolName) || isTerminalStatus(row.status)) continue
    const rowPid = row.parentId?.trim() || ''
    if (pid === rowPid) return i
  }
  return -1
}

/** 按 toolCallId 合并；无 id 或 id 不一致时并入同名未完成调用，避免「执行中 + 已完成」各一条 */
function mergeToolCallTimeline(
  prev: ToolCallInfo[] | undefined,
  incoming: ToolCallInfo,
  contentOffset: number,
): ToolCallInfo[] {
  const list = [...(prev ?? [])]
  const id = incoming.toolCallId?.trim()
  let idx = id ? list.findIndex((x) => x.toolCallId?.trim() === id) : -1
  // 有独立 id 时不要并入同名未完成项，否则并行 Agent/子智能体会被合成一条
  if (idx < 0 && !id && incoming.toolName) {
    idx = findLastOpenTool(list, incoming.toolName, incoming.parentId)
  }
  if (idx < 0 && id && incoming.toolName && isTerminalStatus(incoming.status)) {
    idx = findLastOpenTool(list, incoming.toolName, incoming.parentId)
  }
  if (idx < 0) {
    list.push({
      ...incoming,
      startedAt: incoming.startedAt || Date.now(),
      status: incoming.status || 'running',
      contentOffset: incoming.contentOffset ?? contentOffset,
    })
    return list
  }
  const cur = list[idx]
  const result = incoming.result != null && String(incoming.result).trim() !== '' ? incoming.result : cur.result
  const startedAt = cur.startedAt || incoming.startedAt
  const nextStatus = mergeToolStatus(
    cur.status,
    incoming.status,
    isSubagentTool(cur) || isSubagentTool(incoming),
  )
  const reopened = isTerminalStatus(cur.status) && !isTerminalStatus(nextStatus)
  const endedAt = isAwaitingStatus(nextStatus) || reopened
    ? undefined
    : (incoming.endedAt || cur.endedAt)
  list[idx] = {
    ...cur,
    ...incoming,
    toolCallId: cur.toolCallId || id,
    toolName: cur.toolName || incoming.toolName,
    displayName: incoming.displayName || cur.displayName,
    iconKind: incoming.iconKind || cur.iconKind,
    args: mergeArgs(cur.args, incoming.args),
    result,
    status: nextStatus,
    startedAt,
    endedAt,
    contentOffset: cur.contentOffset ?? incoming.contentOffset ?? contentOffset,
    durationMs: isAwaitingStatus(nextStatus)
      ? undefined
      : (incoming.durationMs
        ?? (startedAt && endedAt ? Math.max(0, endedAt - startedAt) : cur.durationMs)),
    durationSeconds: isAwaitingStatus(nextStatus)
      ? undefined
      : (incoming.durationSeconds ?? cur.durationSeconds),
    eventType: incoming.eventType || cur.eventType,
    context: pickNonEmpty(incoming.context, cur.context),
    summary: pickNonEmpty(incoming.summary, cur.summary),
    resultText: pickNonEmpty(incoming.resultText, cur.resultText),
    error: pickNonEmpty(incoming.error, cur.error),
    inlineDiff: pickNonEmpty(incoming.inlineDiff, cur.inlineDiff),
    risk: pickNonEmpty(incoming.risk, cur.risk),
    findings: incoming.findings?.length ? incoming.findings : cur.findings,
    redacted: incoming.redacted ?? cur.redacted,
    stderr: pickNonEmpty(incoming.stderr, cur.stderr),
    progress: pickNonEmpty(incoming.progress, cur.progress),
    todos: incoming.todos ?? cur.todos,
    subagent: incoming.subagent ?? cur.subagent,
    taskIndex: incoming.taskIndex ?? cur.taskIndex,
    taskCount: incoming.taskCount ?? cur.taskCount,
    parentId: pickNonEmpty(incoming.parentId, cur.parentId),
    childSessionId: pickNonEmpty(incoming.childSessionId, cur.childSessionId),
    childToolName: pickNonEmpty(incoming.childToolName, cur.childToolName),
    agentCode: pickNonEmpty(incoming.agentCode, cur.agentCode),
    agentIcon: pickNonEmpty(incoming.agentIcon, cur.agentIcon),
    apiCalls: incoming.apiCalls ?? cur.apiCalls,
    toolCount: incoming.toolCount ?? cur.toolCount,
    awaitingBackground: incoming.awaitingBackground ?? cur.awaitingBackground,
  }
  return list
}

function pickNonEmpty(a?: string, b?: string): string | undefined {
  if (a != null && String(a).trim() !== '') return a
  if (b != null && String(b).trim() !== '') return b
  return undefined
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<Message[]>([])
  const inputText = ref('')
  const conversationId = ref('')
  /** 会话消息已切换完成的序号；供视图在换会话后贴底，避免先滚旧消息 */
  const sessionViewEpoch = ref(0)
  const sessionGoal = ref<SessionGoal | null>(null)
  const historySessions = ref<HistorySession[]>([])
  const searchKeyword = ref('')
  const historyAgentFilter = ref('')
  /** 本地已知正在输出的会话（含已断开订阅但仍在服务端运行的） */
  const streamingSessionIds = ref<Set<string>>(new Set())
  /** 正在向服务端发送停止请求的会话（防止停止按钮连点） */
  const stoppingSessionIds = ref<Set<string>>(new Set())
  /** 用户主动停止后，在服务端 Run 真正结束前抑制自动续传与列表回标 */
  const userStoppedSessionIds = new Set<string>()
  const compactingSessionId = ref('')
  type ActiveStream = { abort: AbortController; runId: string; lastSeq: number }
  const activeStreams = new Map<string, ActiveStream>()
  /** 当前 messages 所属会话；避免切换中途 hydrate 误判已加载 */
  let messagesLoadedForSessionId = ''
  /** 续传/发送代次；abort 或新附着前递增，使旧 SSE handler 失效 */
  const streamGenerations = new Map<string, number>()
  const attachInflight = new Map<string, Promise<void>>()
  const isLoading = computed(() => {
    const id = conversationId.value
    return Boolean(id) && streamingSessionIds.value.has(id)
  })
  const isStopping = computed(() => {
    const id = conversationId.value
    return Boolean(id) && stoppingSessionIds.value.has(id)
  })
  const contextCompacting = computed(() => {
    const id = conversationId.value
    return Boolean(id) && compactingSessionId.value === id
  })
  const historyLoading = ref(false)
  const historyLoadingMore = ref(false)
  const historyHasMore = ref(true)
  const historyLoadError = ref('')
  const historyPage = ref(0)
  let historyFetchSeq = 0
  const historyAgentFacets = ref<Array<{ groupKey: string; label: string }>>([])
  let historyCursorUpdatedAt = ''
  let historyCursorId = ''
  let historyFilterTimer: ReturnType<typeof setTimeout> | null = null

  const hasMessages = computed(() => messages.value.length > 0)
  const sessionUsage = computed(() => accumulateSessionUsage(messages.value, isLoading.value))
  const nowTick = ref(Date.now())
  let durationTimer: ReturnType<typeof setInterval> | null = null

  watch(isLoading, (loading) => {
    if (loading) {
      nowTick.value = Date.now()
      if (!durationTimer) {
        durationTimer = setInterval(() => {
          nowTick.value = Date.now()
        }, 1000)
      }
      return
    }
    if (durationTimer) {
      clearInterval(durationTimer)
      durationTimer = null
    }
  })

  const sessionDurationMs = computed(() => sessionActiveDurationMs(messages.value, {
    streaming: isLoading.value,
    now: nowTick.value,
  }))

  const sessionStats = computed(() => {
    const usage = sessionUsage.value
    const inputTokens = usage?.promptTokens ?? 0
    const outputTokens = usage?.completionTokens ?? 0
    const totalTokens = usage?.totalTokens
      ?? ((inputTokens || outputTokens) ? inputTokens + outputTokens : 0)
    const treeIn = usage?.treePromptTokens ?? 0
    const treeOut = usage?.treeCompletionTokens ?? 0
    const treeTotal = (treeIn || treeOut) ? treeIn + treeOut : 0
    const contextUsed = usage?.contextUsed ?? 0
    const userEst = usage?.userTokensEstimate ?? 0
    const overhead = usage?.promptOverheadTokens ?? 0
    const costUsd = usage?.totalCostUsd ?? 0
    const cacheRead = usage?.cacheReadTokens ?? 0
    const cacheCreate = usage?.cacheCreationTokens ?? 0
    const dash = (n: number) => (n > 0 ? formatTokenCount(n) : '—')
    const contextSource = usage?.estimatedOccupancy
      ? '会话正文粗估（上游尚未返回 /context）'
      : usage?.contextSnapshot
        ? 'Claude SDK /context 快照'
        : usage?.sessionSnapshot
          ? 'Hermes Dashboard 会话快照'
          : '最近一次模型请求的 prompt'
    const contextTitle = contextUsed > 0
      ? `当前上下文占用 ${contextUsed} tokens（${contextSource}，与输入框旁进度条相同）`
      : '上游尚未返回当前上下文占用'
    const treeTitle = treeTotal > 0 && treeTotal > totalTokens
      ? `含子任务 ${treeIn + treeOut} tokens（主循环 ${totalTokens || '—'}；来自 SDK modelUsage）`
      : ''
    return {
      durationMs: sessionDurationMs.value,
      durationLabel: formatSessionDuration(sessionDurationMs.value),
      inputTokens,
      outputTokens,
      totalTokens,
      treePromptTokens: treeIn,
      treeCompletionTokens: treeOut,
      treeTotalTokens: treeTotal,
      contextUsed,
      userTokensEstimate: userEst,
      promptOverheadTokens: overhead,
      totalCostUsd: costUsd,
      cacheReadTokens: cacheRead,
      cacheCreationTokens: cacheCreate,
      contextLabel: dash(contextUsed),
      contextTitle,
      inputLabel: dash(inputTokens),
      outputLabel: dash(outputTokens),
      totalLabel: dash(totalTokens),
      treeLabel: treeTotal > 0 ? dash(treeTotal) : '—',
      costLabel: costUsd > 0 ? formatUsdCost(costUsd) : '—',
      inputTitle: inputTokens > 0
        ? `累计输入 ${inputTokens} tokens（各轮主循环 prompt 之和；工具循环会重复计入历史${contextUsed ? `，大于当前占用 ${dash(contextUsed)} 属正常` : ''}）`
        : '上游尚未返回 prompt_tokens',
      outputTitle: outputTokens > 0
        ? `累计输出 ${outputTokens} tokens（各轮主循环 completion 之和）`
        : '上游尚未返回 completion_tokens',
      totalTitle: totalTokens > 0
        ? `累计调用 ${totalTokens} tokens（主循环输入 ${inputTokens} + 输出 ${outputTokens}${treeTitle ? `；${treeTitle}` : ''}）`
        : '暂无用量',
      treeTitle: treeTitle || (treeTotal > 0 ? `含子任务累计 ${treeTotal} tokens（SDK modelUsage 各轮之和）` : ''),
      costTitle: costUsd > 0
        ? `累计估算成本 ${formatUsdCost(costUsd)}（Claude SDK total_cost_usd 各轮之和，非账单）`
        : '',
      cacheTitle: (cacheRead || cacheCreate)
        ? `缓存读取 ${dash(cacheRead)} · 缓存写入 ${dash(cacheCreate)}`
        : '',
    }
  })
  const filteredHistorySessions = computed(() => historySessions.value)

  const historyAgentOptions = computed(() => {
    const officerName = getSystemName()
    const map = new Map<string, string>()
    map.set('', '全部专业智能体')
    for (const f of historyAgentFacets.value) {
      if (f.groupKey) {
        const label = f.groupKey === DIGITAL_OFFICER_KEY ? officerName : (f.label || officerName)
        map.set(f.groupKey, label)
      }
    }
    for (const s of historySessions.value) {
      const key = agentGroupKey(s)
      if (!map.has(key)) {
        const label = key === DIGITAL_OFFICER_KEY ? officerName : (s.agentName || officerName)
        map.set(key, label)
      }
    }
    return [...map.entries()].map(([value, label]) => ({ value, label }))
  })

  const groupedHistorySessions = computed(() => {
    const officerName = getSystemName()
    const groups: Array<{ key: string; label: string; sessions: HistorySession[] }> = []
    const index = new Map<string, number>()
    for (const s of filteredHistorySessions.value) {
      const key = agentGroupKey(s)
      const label = key === DIGITAL_OFFICER_KEY ? officerName : (s.agentName || officerName)
      let i = index.get(key)
      if (i == null) {
        i = groups.length
        index.set(key, i)
        groups.push({ key, label, sessions: [] })
      }
      groups[i].sessions.push(s)
    }
    groups.sort((a, b) => {
      if (a.key === DIGITAL_OFFICER_KEY) return -1
      if (b.key === DIGITAL_OFFICER_KEY) return 1
      return a.label.localeCompare(b.label, 'zh-CN')
    })
    return groups
  })

    async function fetchHistoryPage(_page: number, append: boolean, seq: number) {
    const agentContext = useAgentContextStore()
    await agentContext.ensureAgents()
    if (seq !== historyFetchSeq) return
    const keyword = searchKeyword.value.trim()
    const agentGroup = historyAgentFilter.value.trim()
    const res = await listSessions({
      page: append ? undefined : 1,
      limit: HISTORY_PAGE_SIZE,
      keyword: keyword || undefined,
      agentGroup: agentGroup || undefined,
      cursorUpdatedAt: append && historyCursorUpdatedAt ? historyCursorUpdatedAt : undefined,
      cursorId: append && historyCursorId ? historyCursorId : undefined,
    })
    if (seq !== historyFetchSeq) return
    const mapped = (res.items || []).map((s) => toHistorySession(s, agentContext.agents))
    if (append) {
      const seen = new Set(historySessions.value.map((s) => s.id))
      historySessions.value = [...historySessions.value, ...mapped.filter((s) => !seen.has(s.id))]
    } else {
      historySessions.value = mapped
    }
    historyPage.value = append ? historyPage.value + 1 : 1
    historyHasMore.value = Boolean(res.hasMore)
    if (!append && Array.isArray(res.agentFacets)) {
      historyAgentFacets.value = res.agentFacets
    }
    const lastApi = (res.items || [])[(res.items || []).length - 1]
    if (lastApi?.id && lastApi.updatedAt) {
      historyCursorId = lastApi.id
      historyCursorUpdatedAt =
        typeof lastApi.updatedAt === 'string'
          ? lastApi.updatedAt
          : new Date(lastApi.updatedAt).toISOString()
    }
    historyLoadError.value = ''
    const nextStreaming = new Set<string>()
    for (const id of activeStreams.keys()) {
      if (!userStoppedSessionIds.has(id)) nextStreaming.add(id)
    }
    for (const s of historySessions.value) {
      if (s.streaming && !userStoppedSessionIds.has(s.id)) nextStreaming.add(s.id)
    }
    // 保留仍标记中、且不在本页列表的会话（可能分页未覆盖）
    for (const id of streamingSessionIds.value) {
      if (userStoppedSessionIds.has(id)) continue
      if (activeStreams.has(id) || historySessions.value.some((s) => s.id === id && s.streaming)) {
        nextStreaming.add(id)
      }
    }
    streamingSessionIds.value = nextStreaming
    for (const id of [...userStoppedSessionIds]) {
      const row = historySessions.value.find((s) => s.id === id)
      if (row && !row.streaming) userStoppedSessionIds.delete(id)
    }
    if (conversationId.value) {
      const current = historySessions.value.find((s) => s.id === conversationId.value)
      if (current) sessionGoal.value = normalizeGoal(current.goal)
    }
  }

  async function refreshHistoryFromServer() {
    const seq = ++historyFetchSeq
    historyLoading.value = true
    historyLoadError.value = ''
    try {
      await fetchHistoryPage(1, false, seq)
    } catch (e) {
      if (seq !== historyFetchSeq) return
      historyLoadError.value = e instanceof Error ? e.message : '加载会话列表失败'
      message.error(historyLoadError.value)
    } finally {
      if (seq === historyFetchSeq) historyLoading.value = false
    }
  }

  async function loadMoreHistory() {
    if (historyLoading.value || historyLoadingMore.value || !historyHasMore.value) return
    if (historyLoadError.value) return
    const seq = historyFetchSeq
    historyLoadingMore.value = true
    historyLoadError.value = ''
    try {
      await fetchHistoryPage(historyPage.value + 1, true, seq)
    } catch (e) {
      if (seq !== historyFetchSeq) return
      historyLoadError.value = e instanceof Error ? e.message : '加载更多会话失败'
    } finally {
      if (seq === historyFetchSeq) historyLoadingMore.value = false
    }
  }

  watch(
    () => [searchKeyword.value, historyAgentFilter.value] as const,
    () => {
      if (historyFilterTimer) clearTimeout(historyFilterTimer)
      historyFilterTimer = setTimeout(() => {
        void refreshHistoryFromServer()
      }, 280)
    },
  )

  async function retryHistoryLoad() {
    historyLoadError.value = ''
    if (historyPage.value < 1 || historySessions.value.length === 0) {
      await refreshHistoryFromServer()
      return
    }
    await loadMoreHistory()
  }

  function resolveRouteAgentCode(explicit?: string | null) {
    if (explicit !== undefined) return (explicit || '').trim()
    return (useAgentContextStore().activeAgent?.code || '').trim()
  }

  async function syncChatRoute(
    sessionId: string | null | undefined,
    mode: 'push' | 'replace' = 'replace',
    agentCode?: string | null,
  ) {
    const id = (sessionId || '').trim()
    const agent = resolveRouteAgentCode(agentCode)
    const current = router.currentRoute.value
    const paramId = typeof current.params.sessionId === 'string' ? current.params.sessionId : ''
    const currentAgent = typeof current.query.agent === 'string' ? current.query.agent.trim() : ''
    if (current.name === 'chat' && paramId === id && currentAgent === agent) return
    const loc = {
      name: 'chat' as const,
      params: { sessionId: id || '' },
      query: agent ? { agent } : {},
    }
    if (mode === 'push') await router.push(loc)
    else await router.replace(loc)
  }

  function markStreaming(sessionId: string) {
    const next = new Set(streamingSessionIds.value)
    next.add(sessionId)
    streamingSessionIds.value = next
  }

  function unmarkStreaming(sessionId: string) {
    if (compactingSessionId.value === sessionId) {
      compactingSessionId.value = ''
    }
    if (!streamingSessionIds.value.has(sessionId)) return
    const next = new Set(streamingSessionIds.value)
    next.delete(sessionId)
    streamingSessionIds.value = next
  }

  /** 清除本地误标的 streaming（服务端已无 RUNNING Run） */
  async function reconcileStreamingState(sessionId: string) {
    if (!sessionId) return
    if (wasUserStopped(sessionId) || activeStreams.has(sessionId)) {
      if (wasUserStopped(sessionId)) unmarkStreaming(sessionId)
      return
    }
    if (!streamingSessionIds.value.has(sessionId)) return
    try {
      const active = await getActiveRun(sessionId)
      if (!isActiveRunRunning(active)) unmarkStreaming(sessionId)
    } catch {
      unmarkStreaming(sessionId)
    }
  }

  function markStopping(sessionId: string) {
    const next = new Set(stoppingSessionIds.value)
    next.add(sessionId)
    stoppingSessionIds.value = next
  }

  function unmarkStopping(sessionId: string) {
    if (!stoppingSessionIds.value.has(sessionId)) return
    const next = new Set(stoppingSessionIds.value)
    next.delete(sessionId)
    stoppingSessionIds.value = next
  }

  function noteUserStopped(sessionId: string) {
    if (sessionId) userStoppedSessionIds.add(sessionId)
  }

  function wasUserStopped(sessionId: string) {
    return Boolean(sessionId) && userStoppedSessionIds.has(sessionId)
  }

  function clearUserStopped(sessionId: string) {
    if (sessionId) userStoppedSessionIds.delete(sessionId)
  }

  function bumpStreamGeneration(sessionId: string): number {
    const next = (streamGenerations.get(sessionId) ?? 0) + 1
    streamGenerations.set(sessionId, next)
    return next
  }

  function abortActiveStream(sessionId: string) {
    const active = activeStreams.get(sessionId)
    if (!active) return
    active.abort.abort()
    activeStreams.delete(sessionId)
  }

  function startStreamAttach(sessionId: string, runMeta?: { runId?: string; lastSeq?: number }) {
    abortActiveStream(sessionId)
    const generation = bumpStreamGeneration(sessionId)
    const controller = new AbortController()
    activeStreams.set(sessionId, {
      abort: controller,
      runId: runMeta?.runId || '',
      lastSeq: runMeta?.lastSeq ?? 0,
    })
    return { controller, generation }
  }

  function detachSubscribe(sessionId: string) {
    bumpStreamGeneration(sessionId)
    abortActiveStream(sessionId)
  }

  function findAssistantIndexForSession(sessionId: string, assistantMessageId?: string): number {
    if (conversationId.value !== sessionId) return -1
    if (assistantMessageId) {
      const byId = messages.value.findIndex((m) => m.id === assistantMessageId)
      if (byId >= 0) return byId
    }
    for (let i = messages.value.length - 1; i >= 0; i -= 1) {
      if (messages.value[i].role === 'assistant') return i
    }
    return -1
  }

  async function ensureConversationId(question: string): Promise<string> {
    if (conversationId.value) return conversationId.value
    try {
      const agentContext = useAgentContextStore()
      const active = agentContext.activeAgent
      const boundProfile = active?.hermesProfile?.trim()
      const selectedProfile = useHermesProfileStore().selectedProfile?.trim()
      const hermesProfile = boundProfile
        || selectedProfile
        || (active ? undefined : 'default')
      const session = await createSession(question.slice(0, 20) || '新对话', {
        agentCode: active?.code?.trim() || undefined,
        hermesProfile,
        agentName: active?.name?.trim() || getSystemName(),
      })
      conversationId.value = session.id
      // 本地已有乐观消息：标记已绑定，避免 syncChatRoute 触发 hydrateFromRoute → switchSession 覆盖
      messagesLoadedForSessionId = session.id
      await syncChatRoute(session.id, 'replace', active?.code)
      await refreshHistoryFromServer()
      return session.id
    } catch (e) {
      message.error(e instanceof Error ? e.message : '创建会话失败')
      throw e
    }
  }

  function bindStreamHandlers(
    sessionId: string,
    localAssistantIndex: number,
    opts?: { resetOnAttach?: boolean; generation?: number },
  ) {
    let assistantIndex = localAssistantIndex
    let assistantMessageId = ''
    let finished = false
    const generation = opts?.generation ?? streamGenerations.get(sessionId) ?? 0
    const stale = () => (streamGenerations.get(sessionId) ?? 0) !== generation
    const resolveIndex = () => {
      if (conversationId.value !== sessionId) return -1
      if (assistantMessageId) {
        const byId = messages.value.findIndex((m) => m.id === assistantMessageId)
        if (byId >= 0) {
          assistantIndex = byId
          return byId
        }
      }
      if (assistantIndex >= 0 && assistantIndex < messages.value.length && messages.value[assistantIndex]?.role === 'assistant') {
        return assistantIndex
      }
      return findAssistantIndexForSession(sessionId, assistantMessageId)
    }
    return {
      finished: () => finished,
      handlers: {
        onStarted: (payload: { assistantMessageId?: string; runId?: string; seq?: number }) => {
          if (stale()) return
          const active = activeStreams.get(sessionId)
          if (active) {
            if (payload.runId) active.runId = payload.runId
            if (payload.seq != null) active.lastSeq = payload.seq
          }
          if (payload.assistantMessageId) {
            assistantMessageId = payload.assistantMessageId
            const idx = resolveIndex()
            if (idx >= 0 && messages.value[idx]) {
              messages.value[idx].id = payload.assistantMessageId
              messages.value[idx].status = 'streaming'
              if (opts?.resetOnAttach) {
                messages.value[idx].content = ''
                messages.value[idx].toolCalls = []
                messages.value[idx].toolCalling = true
              }
            }
          }
        },
        onSeq: (seq: number) => {
          if (stale()) return
          const active = activeStreams.get(sessionId)
          if (active) active.lastSeq = seq
        },
        onToken: (textChunk: string) => {
          if (stale()) return
          const idx = resolveIndex()
          if (idx < 0) return
          const current = messages.value[idx]
          if (current && current.role === 'assistant') {
            current.content += textChunk
            current.status = 'streaming'
          }
        },
        onToolCall: (toolCall: import('@/types/chat').ToolCallInfo) => {
          if (stale()) return
          const idx = resolveIndex()
          if (idx < 0) return
          const list = messages.value
          const current = list[idx]
          if (!current || current.role !== 'assistant') return
          const nextToolCalls = mergeToolCallTimeline(
            current.toolCalls,
            toolCall,
            current.content?.length ?? 0,
          )
          const stillRunning = nextToolCalls.some((t) => !isTerminalStatus(t.status))
          list.splice(idx, 1, {
            ...current,
            toolCalling: stillRunning,
            toolCalls: nextToolCalls,
            status: 'streaming',
          })
        },
        onDelegationUpdate: (payload: Record<string, unknown>) => {
          if (stale()) return
          delegationEvents.value = [...delegationEvents.value.slice(-29), payload]
          const toolCallId = typeof payload.toolCallId === 'string' ? payload.toolCallId : ''
          if (!toolCallId) return
          const idx = resolveIndex()
          if (idx < 0) return
          const current = messages.value[idx]
          if (!current?.toolCalls?.length) return
          const next = current.toolCalls.map((t) => {
            if (t.toolCallId !== toolCallId) return t
            return {
              ...t,
              status: typeof payload.status === 'string' ? payload.status : t.status,
              result: typeof payload.summary === 'string' ? payload.summary : t.result,
              delegationId: typeof payload.delegationId === 'string' ? payload.delegationId : (t as ToolCallInfo & { delegationId?: string }).delegationId,
            }
          })
          messages.value.splice(idx, 1, { ...current, toolCalls: next })
        },
        onUsage: (usage: ContextUsage) => {
          if (stale()) return
          const idx = resolveIndex()
          if (idx < 0) return
          const list = messages.value
          const current = list[idx]
          if (!current || current.role !== 'assistant') return
          const prev = current.usage ?? {}
          const next: ContextUsage = { ...prev, ...usage, live: usage.live === true }
          if (usage.contextSnapshot) {
            next.live = false
            next.contextSnapshot = true
            delete next.estimatedOccupancy
          } else if (usage.sessionSnapshot) {
            next.live = false
            next.sessionSnapshot = true
            if (usage.contextUsed != null) next.contextUsed = usage.contextUsed
            if (usage.contextWindow != null) next.contextWindow = usage.contextWindow
            if (usage.contextPercent != null) next.contextPercent = usage.contextPercent
            delete next.estimatedOccupancy
          } else if (prev.contextSnapshot || prev.sessionSnapshot) {
            // result/live 不得覆盖已写入的 SDK /context 或 Dashboard 快照
            next.contextUsed = prev.contextUsed
            next.contextWindow = prev.contextWindow ?? next.contextWindow
            next.contextPercent = prev.contextPercent
            if (prev.contextSnapshot) next.contextSnapshot = true
            if (prev.sessionSnapshot) next.sessionSnapshot = true
            delete next.estimatedOccupancy
          } else if (usage.contextUsed != null && usage.contextUsed > 0) {
            next.contextUsed = usage.contextUsed
            if (usage.contextWindow != null) next.contextWindow = usage.contextWindow
            if (usage.contextPercent != null) next.contextPercent = usage.contextPercent
            delete next.estimatedOccupancy
          } else if (usage.live) {
            delete next.contextUsed
            delete next.contextPercent
          } else {
            delete next.contextUsed
            delete next.contextPercent
          }
          list.splice(idx, 1, { ...current, usage: next })
        },
        onCompact: (event: { phase: string; trigger?: string }) => {
          if (stale()) return
          if (event.phase === 'start') {
            compactingSessionId.value = sessionId
            return
          }
          if (event.phase === 'done' && compactingSessionId.value === sessionId) {
            compactingSessionId.value = ''
          }
        },
        onSuggestions: (items: string[]) => {
          if (stale()) return
          const idx = resolveIndex()
          if (idx < 0) return
          const list = messages.value
          const current = list[idx]
          if (!current || current.role !== 'assistant') return
          list.splice(idx, 1, {
            ...current,
            suggestions: items,
          })
        },
        onStreamWarning: (msg: string) => {
          if (stale()) return
          if (conversationId.value === sessionId) message.warning(msg)
        },
        onGeneratedFile: (file) => {
          if (stale()) return
          void useDataFilesStore().loadFiles()
          const token = typeof file?.publicToken === 'string' ? file.publicToken.trim() : ''
          const name = typeof file?.name === 'string' && file.name.trim() ? file.name.trim() : '文档'
          if (!token) return
          const href =
            typeof file?.href === 'string' && file.href.trim()
              ? file.href.trim()
              : `/QianXunService/data/files/public/${token}`
          const idx = resolveIndex()
          if (idx < 0) return
          const current = messages.value[idx]
          if (!current || current.role !== 'assistant') return
          if (current.content.includes(href) || current.content.includes(token)) return
          current.content += `\n\n[${name}](${href})\n`
        },
        onSessionGoal: (payload: { cleared?: boolean; goal?: SessionGoal | Record<string, string> }) => {
          if (stale()) return
          if (conversationId.value !== sessionId) return
          if (payload.cleared) {
            sessionGoal.value = null
            return
          }
          sessionGoal.value = normalizeGoal(payload.goal as SessionGoal)
        },
        onDone: (payload: { assistantMessageId?: string; cancelled?: boolean }) => {
          if (stale()) return
          finished = true
          if (compactingSessionId.value === sessionId) compactingSessionId.value = ''
          if (payload.assistantMessageId) assistantMessageId = payload.assistantMessageId
          const idx = resolveIndex()
          if (idx >= 0) {
            const current = messages.value[idx]
            if (current && current.role === 'assistant') {
              current.toolCalling = false
              current.timestamp = Date.now()
              const prevUser = [...messages.value.slice(0, idx)].reverse().find((m) => m.role === 'user')
              const started = prevUser?.timestamp
              if (typeof started === 'number' && started > 0) {
                current.usage = {
                  ...current.usage,
                  generationMs: Math.max(0, Date.now() - started),
                  live: false,
                }
              }
              current.status = payload.cancelled ? 'cancelled' : 'completed'
              current.toolCalls = closeOpenTools(
                current.toolCalls,
                payload.cancelled ? 'error' : 'completed',
              )
              if (payload.assistantMessageId) current.id = payload.assistantMessageId
            }
          }
        },
        onError: (errorMessage: string) => {
          if (stale()) return
          finished = true
          if (compactingSessionId.value === sessionId) compactingSessionId.value = ''
          const idx = resolveIndex()
          if (idx >= 0) {
            const current = messages.value[idx]
            if (current && current.role === 'assistant') {
              current.toolCalling = false
              current.status = 'error'
              current.toolCalls = closeOpenTools(current.toolCalls, 'error')
              current.content = current.content || `请求失败：${errorMessage}`
            }
          }
        },
      },
    }
  }

  function staleGeneration(sessionId: string, generation: number) {
    return (streamGenerations.get(sessionId) ?? 0) !== generation
  }

  async function attachExistingStream(sessionId: string) {
    if (!sessionId || wasUserStopped(sessionId)) {
      if (wasUserStopped(sessionId)) unmarkStreaming(sessionId)
      return
    }
    const inflight = attachInflight.get(sessionId)
    if (inflight) {
      await inflight
      return
    }
    if (activeStreams.has(sessionId)) return

    const task = (async () => {
      let activeInfo: Awaited<ReturnType<typeof getActiveRun>> = null
      try {
        activeInfo = await getActiveRun(sessionId)
      } catch {
        unmarkStreaming(sessionId)
        return
      }
      if (!isActiveRunRunning(activeInfo) || wasUserStopped(sessionId)) {
        unmarkStreaming(sessionId)
        return
      }
      if (activeStreams.has(sessionId)) return
      markStreaming(sessionId)
      const { controller, generation } = startStreamAttach(sessionId, {
        runId: activeInfo.runId || '',
        lastSeq: 0,
      })
      // 从 0 回放缓冲并重置本地助手正文，避免 DB 节流导致缺口
      const localIdx = findAssistantIndexForSession(sessionId, activeInfo.assistantMessageId || undefined)
      const bound = bindStreamHandlers(sessionId, localIdx, { resetOnAttach: true, generation })
      const maxAttempts = 4
      for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
        if (controller.signal.aborted || wasUserStopped(sessionId) || staleGeneration(sessionId, generation)) {
          activeStreams.delete(sessionId)
          if (wasUserStopped(sessionId)) unmarkStreaming(sessionId)
          return
        }
        try {
          await subscribeChatStream({
            sessionId,
            afterSeq: 0,
            signal: controller.signal,
            requireDone: true,
            ...bound.handlers,
          })
          break
        } catch (error) {
          if (isAbortError(error) || controller.signal.aborted || wasUserStopped(sessionId) || staleGeneration(sessionId, generation)) {
            activeStreams.delete(sessionId)
            if (wasUserStopped(sessionId)) unmarkStreaming(sessionId)
            return
          }
          if (attempt >= maxAttempts - 1) {
            if (conversationId.value === sessionId) {
              message.warning('输出续传中断，请稍后刷新会话')
            }
            break
          }
          await new Promise((r) => setTimeout(r, 400 * (2 ** attempt)))
          if (wasUserStopped(sessionId) || controller.signal.aborted || staleGeneration(sessionId, generation)) {
            activeStreams.delete(sessionId)
            unmarkStreaming(sessionId)
            return
          }
          const still = await getActiveRun(sessionId).catch(() => null)
          if (!isActiveRunRunning(still) || wasUserStopped(sessionId)) break
        }
      }
      activeStreams.delete(sessionId)
      if (staleGeneration(sessionId, generation)) return
      if (bound.finished() || wasUserStopped(sessionId)) unmarkStreaming(sessionId)
      else {
        // 可能仍在跑但订阅失败：保留侧栏标记，靠列表刷新校正
        markStreaming(sessionId)
      }
      void refreshHistoryFromServer()
    })()

    attachInflight.set(sessionId, task)
    try {
      await task
    } finally {
      if (attachInflight.get(sessionId) === task) attachInflight.delete(sessionId)
    }
  }

  const delegationEvents = ref<Array<Record<string, unknown>>>([])

  async function sendMessage(
    text?: string,
    attachments?: Array<{ id: string; name: string }>,
    opts?: {
      skillName?: string
      goal?: SessionGoal
      clearGoal?: boolean
      agentsStatus?: boolean
      slashCommand?: string
    },
  ) {
    const attached = (attachments ?? []).filter((a) => a.id?.trim())
    const attachedIds = attached.map((a) => a.id.trim())
    let content = (text || inputText.value).trim()
      || (attachedIds.length ? '请阅读并理解我上传的文档。' : '')
    if (!content && opts?.clearGoal) content = '/goal clear'
    if (!content && opts?.agentsStatus) content = '【子智能体】查看运行中的任务'
    if (!content && !attachedIds.length) return
    if (conversationId.value && stoppingSessionIds.value.has(conversationId.value)) {
      message.warning('正在停止上一轮输出，请稍后再发送')
      return
    }
    if (conversationId.value) {
      await reconcileStreamingState(conversationId.value)
    }
    if (isLoading.value) return

    const userMessage: Message = {
      id: generateId(),
      role: 'user',
      content,
      timestamp: Date.now(),
      attachments: attached.length ? attached : undefined,
    }
    messages.value.push(userMessage)
    inputText.value = ''
    await nextTick()

    const assistantMessage: Message = {
      id: generateId(),
      role: 'assistant',
      content: '',
      toolCalls: [],
      toolCalling: true,
      status: 'streaming',
      timestamp: Date.now(),
    }
    messages.value.push(assistantMessage)
    const assistantIndex = messages.value.length - 1

    let sessionId = ''
    try {
      sessionId = await ensureConversationId(content)
      const userIndex = assistantIndex - 1
      const rollbackOptimistic = (keepUser = false) => {
        if (assistantIndex >= 0 && assistantIndex < messages.value.length && messages.value[assistantIndex]?.role === 'assistant') {
          messages.value.splice(assistantIndex, 1)
        }
        if (!keepUser && userIndex >= 0 && userIndex < messages.value.length && messages.value[userIndex]?.role === 'user') {
          messages.value.splice(userIndex, 1)
        }
      }
      await reconcileStreamingState(sessionId)
      if (activeStreams.has(sessionId)) {
        message.warning('该会话正在输出中，请先停止或等待完成')
        rollbackOptimistic(false)
        return
      }
      // 本地标记已丢（断线/刷新）但后端仍在跑 → 先附着，避免再 POST 触发 409
      const existingRun = await getActiveRun(sessionId).catch(() => null)
      if (isActiveRunRunning(existingRun)) {
        if (wasUserStopped(sessionId)) {
          message.warning('正在停止上一轮输出，请稍后再发送')
          rollbackOptimistic(false)
          return
        }
        message.warning('该会话正在输出中，已为你重新接入，请先停止或等待完成')
        rollbackOptimistic(true)
        markStreaming(sessionId)
        void attachExistingStream(sessionId)
        return
      }
      if (streamingSessionIds.value.has(sessionId)) {
        unmarkStreaming(sessionId)
      }
      const knowledgeStore = useKnowledgeStore()
      const agentContext = useAgentContextStore()
      const active = agentContext.activeAgent
      const agentCode = active?.code?.trim() || undefined
      const boundProfile = active?.hermesProfile?.trim()
      const selectedProfile = useHermesProfileStore().selectedProfile?.trim()
      const hermesProfile = boundProfile
        || selectedProfile
        || (active ? undefined : 'default')
      await knowledgeStore.ensureLoaded()
      if (opts?.goal) sessionGoal.value = normalizeGoal(opts.goal)
      if (opts?.clearGoal) sessionGoal.value = null

      const { controller, generation } = startStreamAttach(sessionId)
      clearUserStopped(sessionId)
      markStreaming(sessionId)
      const bound = bindStreamHandlers(sessionId, assistantIndex, { generation })
      let reattachAfter = false

      try {
        await streamChat({
          sessionId,
          content,
          modelCode: knowledgeStore.selectedModel || undefined,
          agentCode,
          hermesProfile,
          fileIds: attachedIds.length ? attachedIds : undefined,
          skillName: opts?.skillName?.trim() || undefined,
          goal: opts?.goal,
          clearGoal: opts?.clearGoal || undefined,
          agentsStatus: opts?.agentsStatus || undefined,
          slashCommand: opts?.slashCommand?.trim() || undefined,
          signal: controller.signal,
          ...bound.handlers,
        })
      } catch (error) {
        if (isAbortError(error) || controller.signal.aborted || wasUserStopped(sessionId)) {
          // Abort / 主动停止：切会话仅断开订阅；停止按钮已自行 unmark，禁止当断线续传
        } else if (isChatConflictError(error)) {
          // 竞态：发送瞬间另一端已占用 → 回滚 assistant 占位并附着已有输出
          rollbackOptimistic(true)
          message.warning(error instanceof Error ? error.message : '该会话正在输出中，请先停止或等待完成')
          reattachAfter = true
        } else {
          // 断线/未收到 done：若后端仍在跑，保留 streaming 并续传，避免再发触发 409
          const stillRunning = await getActiveRun(sessionId).catch(() => null)
          if (isActiveRunRunning(stillRunning) && !wasUserStopped(sessionId)) {
            if (conversationId.value === sessionId) {
              message.warning('连接中断，正在重新接入输出…')
            }
            reattachAfter = true
          } else {
            const idx = findAssistantIndexForSession(sessionId)
            const current = idx >= 0 ? messages.value[idx] : null
            if (current && current.role === 'assistant') {
              current.toolCalling = false
              current.status = 'error'
              current.toolCalls = closeOpenTools(current.toolCalls, 'error')
              const msg = error instanceof Error ? error.message : '请求失败，请稍后重试'
              current.content = current.content || `请求失败：${msg}`
            }
            unmarkStreaming(sessionId)
          }
        }
      } finally {
        activeStreams.delete(sessionId)
        if (bound.finished() || wasUserStopped(sessionId)) {
          unmarkStreaming(sessionId)
          const idx = findAssistantIndexForSession(sessionId)
          if (idx >= 0 && messages.value[idx]?.role === 'assistant') {
            messages.value[idx].toolCalling = false
            messages.value[idx].timestamp = Date.now()
            if (messages.value[idx].status === 'streaming') {
              messages.value[idx].status = wasUserStopped(sessionId) ? 'cancelled' : 'completed'
            }
            if (messages.value[idx].status !== 'streaming') {
              messages.value[idx].toolCalls = closeOpenTools(
                messages.value[idx].toolCalls,
                messages.value[idx].status === 'error' || messages.value[idx].status === 'cancelled'
                  ? 'error'
                  : 'completed',
              )
            }
          }
        } else if (reattachAfter) {
          markStreaming(sessionId)
        }
        void refreshHistoryFromServer()
      }
      if (reattachAfter && !wasUserStopped(sessionId)) {
        void attachExistingStream(sessionId)
      }
    } catch (error) {
      const current = messages.value[assistantIndex]
      if (current && current.role === 'assistant') {
        current.toolCalling = false
        current.status = 'error'
        current.toolCalls = closeOpenTools(current.toolCalls, 'error')
        if (!isAbortError(error)) {
          const msg = error instanceof Error ? error.message : '请求失败，请稍后重试'
          current.content = current.content || `请求失败：${msg}`
        }
      }
      if (sessionId) {
        activeStreams.delete(sessionId)
        unmarkStreaming(sessionId)
      }
    }
  }

  async function stopStreaming() {
    const sessionId = conversationId.value
    if (!sessionId) return
    if (stoppingSessionIds.value.has(sessionId)) return
    if (!streamingSessionIds.value.has(sessionId) && !activeStreams.has(sessionId)) return
    markStopping(sessionId)
    noteUserStopped(sessionId)
    bumpStreamGeneration(sessionId)
    const active = activeStreams.get(sessionId)
    active?.abort.abort()
    activeStreams.delete(sessionId)
    unmarkStreaming(sessionId)
    const last = messages.value[messages.value.length - 1]
    if (last?.role === 'assistant') {
      last.toolCalling = false
      if (last.status === 'streaming') last.status = 'cancelled'
    }
    try {
      await stopChatStream(sessionId)
    } catch {
      /* 可能已结束 */
    } finally {
      unmarkStopping(sessionId)
    }
  }

  function newConversation() {
    const prev = conversationId.value
    if (prev) detachSubscribe(prev)
    messages.value = []
    messagesLoadedForSessionId = ''
    inputText.value = ''
    conversationId.value = ''
    sessionGoal.value = null
    syncChatRoute('', 'replace', useAgentContextStore().activeAgent?.code)
    void refreshHistoryFromServer()
  }

  async function switchSession(id: string, opts?: { syncRoute?: boolean }) {
    const targetId = (id || '').trim()
    if (!targetId) return
    // 重复点击当前会话：不重拉消息，避免主区/侧栏抖动
    if (targetId === conversationId.value && messages.value.length > 0) {
      const alreadyBound = messagesLoadedForSessionId === targetId
      const localBusy = activeStreams.has(targetId) || streamingSessionIds.value.has(targetId)
      if (alreadyBound || localBusy) {
        if (!alreadyBound) messagesLoadedForSessionId = targetId
        if (streamingSessionIds.value.has(targetId) && !activeStreams.has(targetId) && !wasUserStopped(targetId)) {
          void attachExistingStream(targetId)
        }
        return
      }
    }
    const prev = conversationId.value
    if (prev && prev !== targetId) {
      detachSubscribe(prev)
    }
    inputText.value = ''
    conversationId.value = targetId
    messagesLoadedForSessionId = ''
    const row = historySessions.value.find((s) => s.id === targetId)
    if (opts?.syncRoute !== false) await syncChatRoute(targetId, 'push', row?.agentCode)
    sessionGoal.value = normalizeGoal(row?.goal)
    const agentContext = useAgentContextStore()
    const knowledgeStore = useKnowledgeStore()
    await agentContext.ensureAgents()
    await knowledgeStore.ensureLoaded()
    let boundAgent: AgentRegistryItem | null = null
    if (row?.agentCode) {
      boundAgent = agentContext.agents.find((a) => a.code === row.agentCode) ?? null
      if (boundAgent) {
        agentContext.setActiveAgent(boundAgent)
        useHermesProfileStore().syncFromAgent(boundAgent.hermesProfile)
      } else {
        agentContext.clearActiveAgent()
        if (row.hermesProfile) useHermesProfileStore().syncFromAgent(row.hermesProfile)
        else useHermesProfileStore().useDefaultProfile()
      }
    } else {
      agentContext.clearActiveAgent()
      if (row?.hermesProfile && !['default', 'hermes-agent'].includes(row.hermesProfile.toLowerCase())) {
        useHermesProfileStore().syncFromAgent(row.hermesProfile)
      } else {
        useHermesProfileStore().useDefaultProfile()
      }
    }
    knowledgeStore.syncSelectionForActiveAgent(boundAgent)
    try {
      const raw = await listSessionMessages(targetId)
      // 切换期间若用户又点了别的会话，丢弃过期结果
      if (conversationId.value !== targetId) return
      messages.value = sortMessagesChronologically(raw.map(apiMessageToMessage))
      messagesLoadedForSessionId = targetId
      sessionViewEpoch.value += 1
      const hasStreamingMsg = messages.value.some((m) => m.role === 'assistant' && m.status === 'streaming')
      if (!wasUserStopped(targetId) && (hasStreamingMsg || streamingSessionIds.value.has(targetId) || row?.streaming)) {
        markStreaming(targetId)
        void attachExistingStream(targetId)
      }
    } catch (e) {
      if (conversationId.value !== targetId) return
      message.error(e instanceof Error ? e.message : '加载消息失败')
      messages.value = []
      messagesLoadedForSessionId = ''
    }
  }

  /** 根据路由 sessionId 恢复会话（刷新 / 直接打开链接） */
  async function hydrateFromRoute(sessionId: string | undefined | null) {
    const id = (sessionId || '').trim()
    if (!id) {
      if (conversationId.value) {
        detachSubscribe(conversationId.value)
      }
      messages.value = []
      messagesLoadedForSessionId = ''
      conversationId.value = ''
      sessionGoal.value = null
      return
    }
    // 当前会话已有本地消息（含首条发送的乐观写入）或正在流式输出时，勿重拉覆盖
    if (conversationId.value === id && messages.value.length > 0) {
      if (messagesLoadedForSessionId !== id) messagesLoadedForSessionId = id
      if (streamingSessionIds.value.has(id) && !activeStreams.has(id) && !wasUserStopped(id)) {
        void attachExistingStream(id)
      }
      return
    }
    await switchSession(id, { syncRoute: false })
  }

  async function renameSession(id: string, title: string) {
    const t = title.trim()
    if (!t) {
      message.warning('标题不能为空')
      return
    }
    try {
      await updateSessionTitle(id, t)
      const row = historySessions.value.find((s) => s.id === id)
      if (row) row.title = t
      message.success('会话已重命名')
      await refreshHistoryFromServer()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '重命名失败')
      throw e
    }
  }

  async function deleteSession(id: string) {
    try {
      await apiDeleteSession(id)
      historySessions.value = historySessions.value.filter((s) => s.id !== id)
      if (conversationId.value === id) {
        detachSubscribe(id)
        unmarkStreaming(id)
        unmarkStopping(id)
        clearUserStopped(id)
        try { await stopChatStream(id) } catch { /* ignore */ }
        messages.value = []
        inputText.value = ''
        conversationId.value = ''
        sessionGoal.value = null
        syncChatRoute('', 'replace', useAgentContextStore().activeAgent?.code)
        await refreshHistoryFromServer()
      } else {
        await refreshHistoryFromServer()
      }
      message.success('会话已删除')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败')
      throw e
    }
  }

  /** 智能体被删除后，丢掉本地历史并退出该智能体当前会话 */
  function dropSessionsOfAgent(agentCode?: string | null, hermesProfile?: string | null) {
    const code = (agentCode || '').trim()
    const profile = (hermesProfile || '').trim()
    const match = (s: HistorySession) =>
      (!!code && s.agentCode === code)
      || (!!profile && profile.toLowerCase() !== 'default' && (s.hermesProfile || '').toLowerCase() === profile.toLowerCase())
    const hit = historySessions.value.filter(match)
    if (!hit.length) {
      void refreshHistoryFromServer()
      return
    }
    const ids = new Set(hit.map((s) => s.id))
    historySessions.value = historySessions.value.filter((s) => !ids.has(s.id))
    if (conversationId.value && ids.has(conversationId.value)) {
      newConversation()
    } else {
      void refreshHistoryFromServer()
    }
  }

  async function clearSessionGoal() {
    if (!conversationId.value) {
      sessionGoal.value = null
      return
    }
    if (isLoading.value) stopStreaming()
    await sendMessage('/goal clear', undefined, { clearGoal: true })
  }

  function setInputText(text: string) {
    inputText.value = text
  }

  return {
    messages,
    inputText,
    isLoading,
    isStopping,
    conversationId,
    sessionViewEpoch,
    sessionGoal,
    historySessions,
    historyLoading,
    historyLoadingMore,
    historyHasMore,
    historyLoadError,
    searchKeyword,
    historyAgentFilter,
    hasMessages,
    sessionUsage,
    contextCompacting,
    sessionStats,
    filteredHistorySessions,
    groupedHistorySessions,
    historyAgentOptions,
    streamingSessionIds,
    delegationEvents,
    refreshHistoryFromServer,
    loadMoreHistory,
    retryHistoryLoad,
    hydrateFromRoute,
    attachExistingStream,
    renameSession,
    deleteSession,
    dropSessionsOfAgent,
    sendMessage,
    stopStreaming,
    newConversation,
    switchSession,
    setInputText,
    clearSessionGoal,
  }
})
