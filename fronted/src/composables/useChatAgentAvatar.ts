import { computed } from 'vue'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import { chatAgentAvatarSource } from '@/utils/agentDisplay'

/** 聊天顶栏 / 欢迎页 / 助手气泡共用的当前智能体头像 */
export function useChatAgentAvatar() {
  const agentContext = useAgentContextStore()
  const hermesProfileStore = useHermesProfileStore()

  return computed(() => {
    const agent = agentContext.activeAgent
    const profile = hermesProfileStore.selectedProfile
    if (agent?.code?.trim()) {
      const fromList = agentContext.agents.find((a) => a.code === agent.code)
      return chatAgentAvatarSource({
        agent: {
          ...agent,
          icon: (agent.icon || fromList?.icon || '').trim(),
        },
      })
    }
    return chatAgentAvatarSource({
      agent,
      hermesProfile: profile,
      profileLabel: agentContext.nameForProfile(profile),
    })
  })
}
