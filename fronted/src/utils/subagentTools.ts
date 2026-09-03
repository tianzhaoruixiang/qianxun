import type { ToolCallInfo } from '@/types/chat'

const GENERIC_SUBAGENT_LABELS = new Set([
  'agent',
  'task',
  'subagent',
  'team',
  '子智能体',
  '子任务（旧）',
  '墨川小助手',
  '小助手',
  '专业智能体',
])

/** 顶层子智能体/委派（可并行展示） */
export function toolAgentCode(tool?: ToolCallInfo | null): string {
  if (!tool) return ''
  const direct = (tool.agentCode || '').trim()
  if (direct) return direct
  const raw = (tool.args || '').trim()
  if (!raw.startsWith('{')) return ''
  try {
    const args = JSON.parse(raw) as { agentCode?: unknown }
    return typeof args.agentCode === 'string' ? args.agentCode.trim() : ''
  } catch {
    return ''
  }
}

/** 工具目录名 / Agent·Task 默认名，不能当智能体名称展示 */
export function isGenericSubagentLabel(name?: string | null, catalogLabel?: string | null): boolean {
  const n = (name || '').trim()
  if (!n) return true
  if (catalogLabel?.trim() && n === catalogLabel.trim()) return true
  if (GENERIC_SUBAGENT_LABELS.has(n) || GENERIC_SUBAGENT_LABELS.has(n.toLowerCase())) return true
  return false
}

/** 同一子任务刷新后头像不变；优先 toolCallId */
export function subagentPortraitSeed(tool: ToolCallInfo, fallbackKey: string): string {
  const id = tool.toolCallId?.trim()
  if (id) return id
  const bits = [
    tool.toolName || '',
    tool.taskIndex != null ? String(tool.taskIndex) : '',
    tool.startedAt != null ? String(tool.startedAt) : '',
    (tool.args || '').slice(0, 80),
  ].filter(Boolean)
  if (bits.length) return bits.join(':')
  return fallbackKey || 'subagent'
}

export function resolveSubagentMateName(opts: {
  tool: ToolCallInfo
  registryName?: string | null
  sessionAgentName?: string | null
  catalogLabel?: string | null
}): string {
  const registry = (opts.registryName || '').trim()
  if (registry) return registry
  const fromServer = opts.tool.displayName?.trim()
  if (toolAgentCode(opts.tool)) {
    if (fromServer && !isGenericSubagentLabel(fromServer, opts.catalogLabel)) return fromServer
    return '专业智能体'
  }
  const session = (opts.sessionAgentName || '').trim()
  if (session) return session
  if (fromServer && !isGenericSubagentLabel(fromServer, opts.catalogLabel)) return fromServer
  return '专业智能体'
}

export function isSubagentTool(tool?: ToolCallInfo | null): boolean {
  if (!tool) return false
  if (tool.subagent) return true
  const n = (tool.toolName || '').trim().toLowerCase()
  return n === 'agent' || n === 'task' || n === 'subagent' || n.includes('delegate')
}

export function toolStatusOf(tool?: ToolCallInfo | null): string {
  return (tool?.status || 'running').toLowerCase()
}

export function isRunningTool(tool?: ToolCallInfo | null): boolean {
  const s = toolStatusOf(tool)
  return s === 'started' || s === 'running' || s === 'awaiting' || s === 'background' || s === 'submitted'
}

export function isAwaitingTool(tool?: ToolCallInfo | null): boolean {
  const s = toolStatusOf(tool)
  return s === 'awaiting' || s === 'background'
}

export function isErrorTool(tool?: ToolCallInfo | null): boolean {
  if (!tool) return false
  if (toolStatusOf(tool) === 'error') return true
  return !!(tool.error && String(tool.error).trim())
}

export function descendantsOf(root: ToolCallInfo, all: ToolCallInfo[] | undefined): ToolCallInfo[] {
  const parent = root.toolCallId?.trim()
  if (!parent || !all?.length) return []
  const result: ToolCallInfo[] = []
  const queue = [parent]
  const seen = new Set<string>([parent])
  while (queue.length) {
    const pid = queue.shift()!
    for (const child of all) {
      if (child.parentId?.trim() !== pid) continue
      const cid = child.toolCallId?.trim()
      if (cid) {
        if (seen.has(cid)) continue
        seen.add(cid)
        queue.push(cid)
      }
      result.push(child)
    }
  }
  return result
}

export type EffectiveToolStatus = 'running' | 'awaiting' | 'completed' | 'error'

/**
 * 子智能体卡片状态：父级被上游标成 completed 时，只要还有子孙在跑，仍显示进行中。
 */
export function effectiveToolStatus(
  tool: ToolCallInfo,
  all?: ToolCallInfo[],
): EffectiveToolStatus {
  const kids = isSubagentTool(tool) ? descendantsOf(tool, all) : []
  const selfRunning = isRunningTool(tool)
  const kidRunning = kids.some((k) => isRunningTool(k))
  if (selfRunning || kidRunning) {
    const kidBusy = kids.some((k) => isRunningTool(k) && !isAwaitingTool(k))
    if (isAwaitingTool(tool) && !kidBusy) return 'awaiting'
    return 'running'
  }
  if (isErrorTool(tool)) return 'error'
  if (kids.some(isErrorTool) && kids.every((k) => !isRunningTool(k))) {
    const allFailed = kids.length > 0 && kids.every(isErrorTool)
    if (allFailed) return 'error'
  }
  return 'completed'
}

export function effectiveStatusClass(
  tool: ToolCallInfo,
  all?: ToolCallInfo[],
): 'running' | 'completed' | 'error' {
  const s = effectiveToolStatus(tool, all)
  if (s === 'error') return 'error'
  if (s === 'running' || s === 'awaiting') return 'running'
  return 'completed'
}

export function effectiveStatusLabel(
  tool: ToolCallInfo,
  all?: ToolCallInfo[],
): string {
  const s = effectiveToolStatus(tool, all)
  if (s === 'error') return '失败'
  if (s === 'awaiting') return '后台执行中'
  if (s === 'running') return '干活中'
  return '完成'
}
