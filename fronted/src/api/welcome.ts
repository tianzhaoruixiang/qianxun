import request from '@/utils/request'
import type { WelcomeBootstrapResponse } from '@/types/api'

export interface WelcomePresets {
  presetChat1: string
  presetChat2: string
  presetChat3: string
  officerPortrait?: string
}

/** 首页/欢迎区配置聚合（文案、推荐问题、工具中文名、画像图例） */
export function fetchWelcomeBootstrap(): Promise<WelcomeBootstrapResponse> {
  return request.post('/welcome/bootstrap', { jsonArg: {} })
}

export function fetchWelcomePresets(): Promise<WelcomePresets> {
  return request.post('/welcome/presets', { jsonArg: {} })
}

export function updateWelcomePresets(presets: WelcomePresets): Promise<WelcomePresets> {
  return request.post('/welcome/presets/update', { jsonArg: presets })
}

export interface SystemSettings {
  systemName: string
  claudeChatModel: string
  claudeChatContextWindow?: number | null
  openaiBaseUrl?: string
  openaiApiKey?: string
  openaiApiKeyMasked?: string
  openaiApiKeyConfigured?: boolean
  /** Mem0 嵌入模型 id（管理员） */
  mem0EmbedderModel?: string
  /** 向量维数（管理员） */
  mem0EmbeddingDims?: number | null
  /** 保存后热更新 Mem0 失败时的提示 */
  mem0ApplyWarning?: string
}

export function fetchWelcomeBrand(): Promise<SystemSettings> {
  return request.post('/welcome/brand', { jsonArg: {} })
}

export function fetchSystemSettings(): Promise<SystemSettings> {
  return request.post('/welcome/system', { jsonArg: {} })
}

export function updateSystemSettings(payload: SystemSettings): Promise<SystemSettings> {
  return request.post('/welcome/system/update', { jsonArg: payload })
}

export function fetchUpstreamModels(payload: {
  openaiBaseUrl?: string
  openaiApiKey?: string
}): Promise<{ models: string[]; items?: { id: string; contextWindow?: number | null }[] }> {
  return request.post('/welcome/system/models', { jsonArg: payload })
}
