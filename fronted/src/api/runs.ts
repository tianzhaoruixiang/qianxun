import request from '@/utils/request'
import type { ActiveRunInfo } from '@/api/chat'

export interface RunSummary {
  runId: string
  traceId?: string | null
  sessionId: string
  userId?: string
  status: string
  assistantMessageId?: string | null
  lastSeq: number
  cancelRequested?: boolean | null
  hermesProfile?: string | null
  agentCode?: string | null
  modelCode?: string | null
  startedAtMs: number
  lastEventAtMs?: number
  toolCallCount?: number
  delegationCount?: number
  contentPreview?: string | null
}

export interface RunMetrics {
  runningCount: number
  totalTracked: number
  uptimeMs: number
  statusCounts: Record<string, number>
  running: RunSummary[]
}

export function listRuns(runningOnly = false, limit = 50): Promise<RunSummary[]> {
  return request.post('/runs/list', { jsonArg: { runningOnly, limit } })
}

export function getRunMetrics(): Promise<RunMetrics> {
  return request.get('/runs/metrics')
}

export type { ActiveRunInfo }
