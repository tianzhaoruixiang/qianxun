import request from '@/utils/request'

export interface ModelRegistryItem {
  id: string
  code: string
  name: string
  provider: string
  enabled: boolean
  baseUrl?: string
  contextWindow?: number
  maxTokens?: number
}

/** 智能体（注册表）/ agent_registry */
export interface AgentRegistryItem {
  id: string
  code: string
  name: string
  category: string
  description?: string
  icon?: string
  modelCode?: string
  /** 空态欢迎主标题；未配置时前端用「你好，我是 {名称}」 */
  welcomeTitle?: string
  /** 空态欢迎简介（副文案）；未配置时用全局默认 */
  welcomeIntro?: string
  /** 预置用户话术（最多三条），点击可直接发起对话 */
  presetChat1?: string
  presetChat2?: string
  presetChat3?: string
  /** 绑定的 Hermes profile */
  hermesProfile?: string
  priority: number
  enabled: boolean
}

export interface UpsertAgentPayload {
  code: string
  name: string
  category?: string
  description?: string
  icon?: string
  modelCode?: string
  welcomeTitle?: string
  welcomeIntro?: string
  presetChat1?: string
  presetChat2?: string
  presetChat3?: string
  hermesProfile?: string
  /** Claude Code profile 的 CLAUDE.md 人设，必填 */
  soulMd: string
  priority?: number
  enabled?: boolean
}

export function listRegistryModels(enabledOnly = true): Promise<ModelRegistryItem[]> {
  return request.post('/registry/models/list', { jsonArg: { enabledOnly } })
}

export function listRegistryAgents(enabledOnly = false): Promise<AgentRegistryItem[]> {
  return request.post('/registry/agents/list', { jsonArg: { enabledOnly } })
}

export function upsertRegistryAgent(payload: UpsertAgentPayload): Promise<AgentRegistryItem> {
  return request.post('/registry/agents/upsert', { jsonArg: payload })
}

export function deleteRegistryAgent(code: string): Promise<void> {
  return request.post('/registry/agents/delete', { jsonArg: { code } })
}
