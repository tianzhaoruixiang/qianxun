import { getSystemName } from '@/utils/systemName'

/** 去掉用户可见文案中的底层引擎品牌，避免暴露 Hermes / Claude 等实现细节。 */
export function sanitizeUserFacingText(text: string): string {
  if (!text) return text
  const officerName = getSystemName()
  return text
    .replace(/Claude\s*Code/gi, '智能体')
    .replace(/\bAnthropic\b/gi, '模型服务')
    .replace(/\bClaude\b/gi, '智能体')
    .replace(/hermes-agent/gi, officerName)
    .replace(/Hermes\s+Dashboard/gi, '智能体服务')
    .replace(/Hermes\s+Desktop/gi, '智能体')
    .replace(/Hermes\s+profile/gi, '智能体配置')
    .replace(/Hermes\s+Agent/gi, '智能体')
    .replace(/LLM\/Hermes/gi, '模型服务')
    .replace(/\bHERMES\b/g, '智能体')
    .replace(/\bHermes\b/g, '智能体')
    .replace(/\bhermes\b/g, '智能体')
    .replace(/赫尔墨斯/g, '智能体')
    .replace(/\.hermes\//gi, '')
    .replace(/live\s*transcript/gi, '运行日志')
}
