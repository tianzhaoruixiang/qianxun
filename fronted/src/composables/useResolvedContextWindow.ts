import { computed } from 'vue'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { useChatStore } from '@/stores/useChatStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import { useKnowledgeStore } from '@/stores/useKnowledgeStore'
import { resolveContextWindow } from '@/utils/contextUsage'

function registryWindowFor(
  list: Array<{ code: string; name: string; contextWindow?: number }>,
  code: string | undefined,
): number | undefined {
  const key = (code || '').trim()
  if (!key) return undefined
  const lower = key.toLowerCase()
  const hit = list.find((m) => m.code === key || m.name === key)
    || list.find((m) => m.code.toLowerCase() === lower || m.name.toLowerCase() === lower)
  return hit?.contextWindow && hit.contextWindow > 0 ? hit.contextWindow : undefined
}

/** 当前对话模型最大上下文：bootstrap / usage / 注册表 / 专业智能体 profile 取第一个正值。 */
export function useResolvedContextWindow() {
  const chatStore = useChatStore()
  const bootstrapStore = useBootstrapStore()
  const knowledgeStore = useKnowledgeStore()
  const hermesProfileStore = useHermesProfileStore()

  const resolvedContextWindow = computed(() => {
    const selected = knowledgeStore.selectedModel
    const stubSelected = selected === 'hermes-agent' || selected === 'qianxun-default'
    return resolveContextWindow({
      runtimeWindow: bootstrapStore.claudeChatContextWindow,
      usageWindow: chatStore.sessionUsage?.contextWindow,
      registryWindow: stubSelected ? undefined : registryWindowFor(knowledgeStore.modelRegistryList, selected),
      hermesProfileWindow: hermesProfileStore.currentContextWindow(),
      hermesModelWindow: registryWindowFor(knowledgeStore.modelRegistryList, hermesProfileStore.currentModelName())
        ?? registryWindowFor(knowledgeStore.modelRegistryList, bootstrapStore.claudeChatModel),
      modelId: bootstrapStore.claudeChatModel || hermesProfileStore.currentModelName(),
    })
  })

  return { resolvedContextWindow }
}
