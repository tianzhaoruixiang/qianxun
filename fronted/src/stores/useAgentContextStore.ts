import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AgentRegistryItem } from '@/api/registry'
import { listRegistryAgents } from '@/api/registry'
import {
  displayAgentName,
  displayNameForHermesProfile,
} from '@/utils/agentDisplay'

/**
 * 从智能体页进入对话时携带的上下文（展示名称、默认模型等）。
 */
export const useAgentContextStore = defineStore('agentContext', () => {
  const activeAgent = ref<AgentRegistryItem | null>(null)
  const agents = ref<AgentRegistryItem[]>([])
  const agentsLoaded = ref(false)

  function setActiveAgent(agent: AgentRegistryItem | null) {
    activeAgent.value = agent
  }

  function clearActiveAgent() {
    activeAgent.value = null
  }

  function replaceAgents(list: AgentRegistryItem[]) {
    agents.value = list
    agentsLoaded.value = true
  }

  async function ensureAgents(force = false) {
    if (agentsLoaded.value && !force) return
    try {
      agents.value = await listRegistryAgents(false)
      agentsLoaded.value = true
    } catch {
      if (!agentsLoaded.value) agents.value = []
    }
  }

  function nameForSession(opts: { agentCode?: string | null; hermesProfile?: string | null; agentName?: string | null }) {
    return displayAgentName({ ...opts, agents: agents.value })
  }

  function nameForProfile(profile: string) {
    return displayNameForHermesProfile(profile, agents.value)
  }

  return {
    activeAgent,
    agents,
    agentsLoaded,
    setActiveAgent,
    clearActiveAgent,
    replaceAgents,
    ensureAgents,
    nameForSession,
    nameForProfile,
  }
})
