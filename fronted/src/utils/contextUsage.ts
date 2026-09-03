import type { ContextUsage, Message } from '@/types/chat'

/** 粗估 token：仅作用户正文对照展示，不参与计费汇总 */
export function estimateTokenCount(text: string | undefined | null): number {
  if (!text) return 0
  let cjk = 0
  let other = 0
  for (const ch of text) {
    const code = ch.codePointAt(0) ?? 0
    if (
      (code >= 0x4e00 && code <= 0x9fff)
      || (code >= 0x3400 && code <= 0x4dbf)
      || (code >= 0x3040 && code <= 0x30ff)
      || (code >= 0xac00 && code <= 0xd7af)
    ) {
      cjk += 1
    } else if (!/\s/.test(ch)) {
      other += 1
    }
  }
  return cjk + Math.ceil(other / 4)
}

function sumOptional(usages: ContextUsage[], pick: (u: ContextUsage) => number | undefined): number {
  let sum = 0
  for (const usage of usages) {
    const v = pick(usage)
    if (typeof v === 'number' && v > 0) {
      sum += v
    }
  }
  return sum
}

function latestContextFields(usages: ContextUsage[]): Pick<ContextUsage, 'contextUsed' | 'contextWindow' | 'contextPercent' | 'contextSnapshot'> {
  // SDK getContextUsage 快照
  for (let i = usages.length - 1; i >= 0; i -= 1) {
    const u = usages[i]
    if (u.contextSnapshot && u.contextUsed != null && u.contextUsed > 0) {
      return {
        contextUsed: u.contextUsed,
        contextWindow: u.contextWindow,
        contextPercent: u.contextPercent,
        contextSnapshot: true,
      }
    }
  }
  // Dashboard session.usage 快照
  for (let i = usages.length - 1; i >= 0; i -= 1) {
    const u = usages[i]
    if (u.sessionSnapshot && u.contextUsed != null && u.contextUsed > 0) {
      return {
        contextUsed: u.contextUsed,
        contextWindow: u.contextWindow,
        contextPercent: u.contextPercent,
        contextSnapshot: false,
      }
    }
  }
  // 落库/回放可能缺少 snapshot 标记但仍有 contextUsed
  for (let i = usages.length - 1; i >= 0; i -= 1) {
    const u = usages[i]
    if (u.contextUsed != null && u.contextUsed > 0) {
      return {
        contextUsed: u.contextUsed,
        contextWindow: u.contextWindow,
        contextPercent: u.contextPercent,
        contextSnapshot: u.contextSnapshot,
      }
    }
  }
  return {}
}

function estimateSessionContentTokens(messages: Message[]): number {
  let sum = 0
  for (const m of messages) {
    sum += estimateTokenCount(m.content)
  }
  return sum
}

function resolveOccupiedTokens(
  occupiedBase: number,
  messages: Message[],
  streaming: boolean,
  streamExtra: number,
): { occupied: number; estimatedOccupancy: boolean } {
  let occupied = occupiedBase > 0 ? occupiedBase : 0
  if (streaming && occupied <= 0 && streamExtra > 0) {
    occupied = streamExtra
  }
  if (occupied > 0) {
    return { occupied, estimatedOccupancy: false }
  }
  const est = estimateSessionContentTokens(messages)
  if (est > 0) {
    return { occupied: est, estimatedOccupancy: true }
  }
  return { occupied: 0, estimatedOccupancy: false }
}

/**
 * 会话用量：只汇总上游（Hermes/OpenAI/Claude SDK）已返回的 usage，不做本地估算抬高。
 * - Dashboard：每条 usage 已是会话累计快照，直接使用最新快照，禁止跨消息重复求和。
 * - Claude SDK：contextUsed 取最新 contextSnapshot；计费按各轮 result.usage 求和；含子任务口径来自 tree* 字段。
 */
export function accumulateSessionUsage(messages: Message[], streaming = false): ContextUsage | undefined {
  if (!messages.length) return undefined

  const usages = messages
    .map((message) => message.usage)
    .filter((usage): usage is ContextUsage => !!usage)
  const lastAssistant = [...messages].reverse().find((m) => m.role === 'assistant')
  const streamExtra = streaming && lastAssistant ? estimateTokenCount(lastAssistant.content) : 0

  if (!usages.length) {
    if (!streamExtra && !messages.length) return undefined
    const { occupied, estimatedOccupancy } = resolveOccupiedTokens(0, messages, streaming, streamExtra)
    if (!occupied) return undefined
    return {
      contextUsed: occupied,
      completionTokens: streamExtra || undefined,
      estimatedOccupancy: estimatedOccupancy || undefined,
      userTokensEstimate: (() => {
        const lastUser = [...messages].reverse().find((m) => m.role === 'user')
        return lastUser ? estimateTokenCount(lastUser.content) : undefined
      })(),
    }
  }

  const latest = usages[usages.length - 1]
  const dashboardSnapshot = [...usages].reverse().find((usage) => usage.sessionSnapshot)
  const lastUser = [...messages].reverse().find((m) => m.role === 'user')
  const lastUserTokens = lastUser ? estimateTokenCount(lastUser.content) : 0
  const contextFields = latestContextFields(usages)

  if (dashboardSnapshot) {
    const contextUsed = dashboardSnapshot.contextUsed ?? contextFields.contextUsed ?? 0
    const { occupied, estimatedOccupancy } = resolveOccupiedTokens(
      contextUsed,
      messages,
      streaming,
      streamExtra,
    )
    const win = dashboardSnapshot.contextWindow ?? contextFields.contextWindow ?? 0
    const percent = dashboardSnapshot.contextPercent ?? contextFields.contextPercent
      ?? (win > 0 && occupied > 0 ? Math.min(100, (occupied * 100) / win) : undefined)
    return {
      ...dashboardSnapshot,
      contextUsed: occupied || undefined,
      contextWindow: win || undefined,
      contextPercent: percent,
      estimatedOccupancy: estimatedOccupancy || undefined,
      userTokensEstimate: lastUserTokens || undefined,
      promptOverheadTokens: occupied > 0 && !estimatedOccupancy
        ? Math.max(0, occupied - lastUserTokens)
        : undefined,
      treePromptTokens: sumOptional(usages, (u) => u.treePromptTokens) || undefined,
      treeCompletionTokens: sumOptional(usages, (u) => u.treeCompletionTokens) || undefined,
      totalCostUsd: sumOptional(usages, (u) => u.totalCostUsd) || undefined,
      cacheReadTokens: sumOptional(usages, (u) => u.cacheReadTokens) || undefined,
      cacheCreationTokens: sumOptional(usages, (u) => u.cacheCreationTokens) || undefined,
    }
  }

  let promptSum = 0
  let completionSum = 0
  let totalSum = 0
  let contextWindow = 0

  for (const usage of usages) {
    if (typeof usage.promptTokens === 'number' && usage.promptTokens > 0) {
      promptSum += usage.promptTokens
    }
    if (typeof usage.completionTokens === 'number' && usage.completionTokens > 0) {
      completionSum += usage.completionTokens
    }
    if (typeof usage.totalTokens === 'number' && usage.totalTokens > 0) {
      totalSum += usage.totalTokens
    }
    if (usage.contextWindow && usage.contextWindow > 0) {
      contextWindow = usage.contextWindow
    }
  }

  const billedTotal = (promptSum > 0 || completionSum > 0)
    ? promptSum + completionSum
    : totalSum
  const treePromptSum = sumOptional(usages, (u) => u.treePromptTokens)
  const treeCompletionSum = sumOptional(usages, (u) => u.treeCompletionTokens)
  const costSum = sumOptional(usages, (u) => u.totalCostUsd)
  const cacheReadSum = sumOptional(usages, (u) => u.cacheReadTokens)
  const cacheCreateSum = sumOptional(usages, (u) => u.cacheCreationTokens)
  const lastPrompt = latest?.contextUsed ?? latest?.promptTokens ?? 0
  const live = latest?.live === true
  const occupiedBase = contextFields.contextUsed ?? 0
  const { occupied, estimatedOccupancy } = resolveOccupiedTokens(
    occupiedBase,
    messages,
    streaming,
    streamExtra,
  )
  const win = contextFields.contextWindow || contextWindow || latest?.contextWindow || 0
  const percent = contextFields.contextPercent
    ?? (win > 0 && occupied > 0 ? Math.min(100, (occupied * 100) / win) : latest?.contextPercent)

  return {
    promptTokens: promptSum || undefined,
    completionTokens: completionSum || undefined,
    totalTokens: billedTotal || undefined,
    contextUsed: occupied || undefined,
    contextWindow: win || undefined,
    contextPercent: percent,
    contextSnapshot: contextFields.contextSnapshot,
    estimatedOccupancy: estimatedOccupancy || undefined,
    live: live || undefined,
    treePromptTokens: treePromptSum || undefined,
    treeCompletionTokens: treeCompletionSum || undefined,
    totalCostUsd: costSum || undefined,
    cacheReadTokens: cacheReadSum || undefined,
    cacheCreationTokens: cacheCreateSum || undefined,
    userTokensEstimate: lastUserTokens || undefined,
    promptOverheadTokens: lastPrompt > 0 && !estimatedOccupancy
      ? Math.max(0, lastPrompt - lastUserTokens)
      : undefined,
  }
}

export function formatTokenCount(n: number): string {
  if (!Number.isFinite(n) || n < 0) return '0'
  if (n >= 1_000_000) {
    const m = n / 1_000_000
    const text = Number.isInteger(m) ? String(m) : m.toFixed(1).replace(/\.0$/, '')
    return `${text}M`
  }
  if (n < 1000) return String(Math.round(n))
  const k = n / 1000
  const text = k >= 100 ? k.toFixed(0) : (Number.isInteger(k) ? String(k) : k.toFixed(1).replace(/\.0$/, ''))
  return `${text}k`
}

export function formatUsdCost(n: number): string {
  if (!Number.isFinite(n) || n < 0) return '$0.00'
  if (n >= 1) return `$${n.toFixed(2)}`
  if (n >= 0.01) return `$${n.toFixed(2)}`
  return `$${n.toFixed(4)}`
}

/**
 * 会话「用时」：各轮用户发出 → 助手结束 的生成时间之和，不含轮与轮之间的空闲。
 * 切回历史会话时以落库的 generationMs 为准（助手 createdAt 只是草稿开始时间，不能当结束时刻）。
 */
export function sessionActiveDurationMs(
  messages: Message[],
  opts?: { streaming?: boolean; now?: number },
): number {
  if (!messages.length) return 0
  const now = opts?.now ?? Date.now()
  const streaming = opts?.streaming === true
  let total = 0
  let lastUserTs: number | null = null
  for (let i = 0; i < messages.length; i++) {
    const m = messages[i]
    if (m.role === 'user') {
      const ts = Number(m.timestamp)
      lastUserTs = Number.isFinite(ts) && ts > 0 ? ts : lastUserTs
      continue
    }
    if (m.role !== 'assistant') continue
    const liveTurn = streaming && i === messages.length - 1
    total += turnGenerationMs(m, lastUserTs, liveTurn, now)
  }
  return Math.max(0, total)
}

function turnGenerationMs(
  assistant: Message,
  userTs: number | null,
  liveTurn: boolean,
  now: number,
): number {
  const stored = assistant.usage?.generationMs
  if (typeof stored === 'number' && Number.isFinite(stored) && stored >= 0 && !liveTurn) {
    return stored
  }
  if (userTs == null) return 0
  if (liveTurn) return Math.max(0, now - userTs)
  if (typeof stored === 'number' && Number.isFinite(stored) && stored >= 0) return stored
  const toolMs = toolSpanMs(assistant)
  if (toolMs > 0) return toolMs
  const ts = Number(assistant.timestamp)
  if (!Number.isFinite(ts) || ts < userTs) return 0
  const delta = ts - userTs
  // 草稿 createdAt 往往只比用户消息晚几毫秒，不能当成整轮耗时
  return delta >= 2_000 ? delta : 0
}

function toolSpanMs(assistant: Message): number {
  const tools = assistant.toolCalls
  if (!tools?.length) return 0
  let minStart = Number.POSITIVE_INFINITY
  let maxEnd = 0
  let sum = 0
  for (const t of tools) {
    const dur = t.durationMs
      ?? (t.durationSeconds != null ? Math.round(t.durationSeconds * 1000) : undefined)
    if (typeof dur === 'number' && dur > 0) sum += dur
    const s = Number(t.startedAt)
    const e = Number(t.endedAt)
    if (Number.isFinite(s) && s > 0) minStart = Math.min(minStart, s)
    if (Number.isFinite(e) && e > 0) maxEnd = Math.max(maxEnd, e)
  }
  if (Number.isFinite(minStart) && maxEnd > minStart) {
    return Math.max(sum, maxEnd - minStart)
  }
  return sum
}

export function formatSessionDuration(ms: number): string {
  if (ms < 1000) return `${Math.max(0, Math.round(ms))}ms`
  if (ms < 60_000) return `${Math.max(1, Math.round(ms / 1000))}s`
  const minutes = Math.floor(ms / 60_000)
  const seconds = Math.round((ms % 60_000) / 1000)
  return `${minutes}分${seconds}秒`
}

export function resolveContextWindow(opts: {
  usageWindow?: number | null
  registryWindow?: number | null
  hermesModelWindow?: number | null
  hermesProfileWindow?: number | null
  runtimeWindow?: number | null
  modelId?: string | null
}): number {
  const pick = (...vals: Array<number | null | undefined>) => {
    for (const v of vals) {
      if (typeof v === 'number' && Number.isFinite(v) && v > 0) return v
    }
    return 0
  }
  return pick(
    opts.runtimeWindow,
    opts.usageWindow,
    opts.hermesProfileWindow,
    opts.hermesModelWindow,
    opts.registryWindow,
    knownContextWindow(opts.modelId),
  )
}

/** 与后端 KnownModelContextWindows 对齐：上游 /models 经常不带窗口时的只读兜底。 */
export function knownContextWindow(modelId?: string | null): number {
  const key = (modelId || '').trim().toLowerCase().replace(/^(openai|anthropic|litellm)\//, '')
  if (!key) return 0
  if (key.startsWith('qwen3.6-plus') || key.startsWith('qwen3-6-plus')
    || key.startsWith('qwen3.5-plus') || key === 'qwen3-plus') {
    return 1_000_000
  }
  if (key.startsWith('claude-sonnet-4-5') || key.startsWith('claude-sonnet-4-6') || key === 'sonnet') {
    return 200_000
  }
  if (key.startsWith('claude-opus-4') || key === 'opus') return 200_000
  if (key.startsWith('claude-haiku-4') || key === 'haiku') return 200_000
  return 0
}
