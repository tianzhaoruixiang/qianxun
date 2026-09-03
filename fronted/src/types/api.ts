/**
 * 推荐问题项
 */
export interface SuggestedQuestion {
  id: string
  text: string
  category?: string
}

/** POST /welcome/bootstrap 聚合配置 */
export interface WelcomeBootstrapResponse {
  disclaimer: string
  greeting: string
  capability: string
  recommendLabel: string
  portraitSeriesALabel: string
  portraitSeriesBLabel: string
  suggestedQuestions: SuggestedQuestion[]
  toolDisplayNames: Record<string, string>
  presetChat1?: string
  presetChat2?: string
  presetChat3?: string
  officerPortrait?: string
  systemName?: string
  claudeChatModel?: string
  claudeChatContextWindow?: number | null
}
