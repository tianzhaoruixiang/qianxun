import type { AgentRegistryItem } from '@/api/registry'
import { DEFAULT_BRAND_NAME } from '@/utils/brandCopy'
import { getSystemName, isDigitalOfficerDisplayName } from '@/utils/systemName'
import { sanitizeUserFacingText } from '@/utils/userFacingCopy'

/** @deprecated 仅用于与历史数据比对；展示请用 getSystemName() */
export const DIGITAL_OFFICER_NAME = DEFAULT_BRAND_NAME
export const UNCATEGORIZED_AGENT_NAME = '未分类'
export const DIGITAL_OFFICER_KEY = '__digital_officer__'

export function isDefaultHermesProfile(profile?: string | null): boolean {
  const p = (profile || '').trim().toLowerCase()
  return !p || p === 'default' || p === 'hermes-agent'
}

export function looksLikeTechnicalId(name?: string | null): boolean {
  const n = (name || '').trim()
  if (!n) return true
  return !/[\u3400-\u9fff]/.test(n)
}

/** 模型编码展示：底层引擎名对用户显示为产品名 */
export function displayModelLabel(model?: string | null): string {
  const m = (model || '').trim()
  if (!m) return ''
  if (m.toLowerCase() === 'hermes-agent') return getSystemName()
  if (/^claude/i.test(m) || /anthropic/i.test(m)) return getSystemName()
  return sanitizeUserFacingText(m)
}

export function agentGroupKey(opts: {
  agentCode?: string | null
  hermesProfile?: string | null
  agentName?: string | null
}): string {
  const code = (opts.agentCode || '').trim()
  if (code) return `code:${code}`
  const name = (opts.agentName || '').trim()
  if (isDigitalOfficerDisplayName(name) || (!code && isDefaultHermesProfile(opts.hermesProfile))) {
    return DIGITAL_OFFICER_KEY
  }
  if (name === UNCATEGORIZED_AGENT_NAME) return 'uncat'
  const profile = (opts.hermesProfile || '').trim()
  if (profile) return `profile:${profile.toLowerCase()}`
  return DIGITAL_OFFICER_KEY
}

export function displayAgentName(opts: {
  agentCode?: string | null
  hermesProfile?: string | null
  agentName?: string | null
  agents?: AgentRegistryItem[]
}): string {
  const agents = opts.agents || []
  const code = (opts.agentCode || '').trim()
  if (code) {
    const hit = agents.find((a) => a.code === code)
    const n = hit?.name?.trim()
    if (n && !looksLikeTechnicalId(n)) return n
  }
  const profile = (opts.hermesProfile || '').trim()
  if (profile && !isDefaultHermesProfile(profile)) {
    const hit = agents.find((a) => (a.hermesProfile || '').trim().toLowerCase() === profile.toLowerCase())
    const n = hit?.name?.trim()
    if (n && !looksLikeTechnicalId(n)) return n
  }
  const stored = (opts.agentName || '').trim()
  if (stored && !looksLikeTechnicalId(stored)) return stored
  if (!code && isDefaultHermesProfile(profile)) return getSystemName()
  if (stored && (stored === code || stored.toLowerCase() === profile.toLowerCase() || looksLikeTechnicalId(stored))) {
    return isDefaultHermesProfile(profile) ? getSystemName() : UNCATEGORIZED_AGENT_NAME
  }
  return isDefaultHermesProfile(profile) ? getSystemName() : UNCATEGORIZED_AGENT_NAME
}

/** 聊天页头像：优先当前注册智能体（含 icon），否则 Hermes profile，默认数智干警 */
export function chatAgentAvatarSource(opts: {
  agent?: Pick<AgentRegistryItem, 'code' | 'name' | 'icon' | 'hermesProfile'> | null
  hermesProfile?: string | null
  profileLabel?: string | null
}): { groupKey: string; label: string; icon: string } {
  const agent = opts.agent
  if (agent?.code?.trim()) {
    return {
      groupKey: agentGroupKey({
        agentCode: agent.code,
        agentName: agent.name,
        hermesProfile: agent.hermesProfile,
      }),
      label: agent.name?.trim() || agent.code,
      icon: (agent.icon || '').trim(),
    }
  }
  const profile = (opts.hermesProfile || '').trim()
  if (profile && !isDefaultHermesProfile(profile)) {
    const label = (opts.profileLabel || '').trim()
    return {
      groupKey: agentGroupKey({ hermesProfile: profile }),
      label: label || UNCATEGORIZED_AGENT_NAME,
      icon: '',
    }
  }
  return {
    groupKey: DIGITAL_OFFICER_KEY,
    label: getSystemName(),
    icon: '',
  }
}

export function displayNameForHermesProfile(
  profileName: string,
  agents: AgentRegistryItem[],
): string {
  const p = (profileName || '').trim()
  if (isDefaultHermesProfile(p)) return getSystemName()
  const hit = agents.find((a) => (a.hermesProfile || '').trim().toLowerCase() === p.toLowerCase())
    || agents.find((a) => a.code.trim().toLowerCase() === p.toLowerCase())
  const n = hit?.name?.trim()
  if (n && !looksLikeTechnicalId(n)) return n
  if (n) return n
  return UNCATEGORIZED_AGENT_NAME
}
