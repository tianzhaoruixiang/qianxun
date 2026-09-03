import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchWelcomeBootstrap } from '@/api/welcome'
import type { SuggestedQuestion } from '@/types/api'
import { claudeToolLabel } from '@/utils/claudeToolLabels'
import { DEFAULT_BRAND_NAME } from '@/utils/brandCopy'
import { CLASSIC_OFFICER_PORTRAIT_ID } from '@/utils/agentPortraits'

export const useBootstrapStore = defineStore('bootstrap', () => {
  const loaded = ref(false)
  const loading = ref(false)
  const loadError = ref<string | null>(null)

  const disclaimer = ref('')
  const greeting = ref('')
  const capability = ref('')
  const recommendLabel = ref('')
  const portraitSeriesALabel = ref('')
  const portraitSeriesBLabel = ref('')
  const suggestedQuestions = ref<SuggestedQuestion[]>([])
  const toolDisplayNames = ref<Record<string, string>>({})
  const presetChat1 = ref('')
  const presetChat2 = ref('')
  const presetChat3 = ref('')
  const officerPortrait = ref(CLASSIC_OFFICER_PORTRAIT_ID)
  const systemName = ref(DEFAULT_BRAND_NAME)
  const claudeChatModel = ref('')
  const claudeChatContextWindow = ref(0)

  function applySystemChrome(name: string) {
    const n = (name || '').trim() || DEFAULT_BRAND_NAME
    systemName.value = n
    if (typeof document !== 'undefined') {
      document.title = n
    }
  }

  async function applyBootstrap(data: Awaited<ReturnType<typeof fetchWelcomeBootstrap>>) {
    disclaimer.value = data.disclaimer || ''
    greeting.value = data.greeting || ''
    capability.value = data.capability || ''
    recommendLabel.value = data.recommendLabel || ''
    portraitSeriesALabel.value = data.portraitSeriesALabel || ''
    portraitSeriesBLabel.value = data.portraitSeriesBLabel || ''
    suggestedQuestions.value = Array.isArray(data.suggestedQuestions) ? data.suggestedQuestions : []
    toolDisplayNames.value =
      data.toolDisplayNames && typeof data.toolDisplayNames === 'object' ? data.toolDisplayNames : {}
    presetChat1.value = data.presetChat1 || ''
    presetChat2.value = data.presetChat2 || ''
    presetChat3.value = data.presetChat3 || ''
    officerPortrait.value = (data.officerPortrait || '').trim() || CLASSIC_OFFICER_PORTRAIT_ID
    applySystemChrome(data.systemName || '')
    claudeChatModel.value = (data.claudeChatModel || '').trim()
    claudeChatContextWindow.value = Number(data.claudeChatContextWindow) > 0
      ? Number(data.claudeChatContextWindow)
      : 0
  }

  async function ensureLoaded() {
    if (loaded.value || loading.value) return
    loading.value = true
    loadError.value = null
    try {
      await applyBootstrap(await fetchWelcomeBootstrap())
      loaded.value = true
    } catch (e) {
      loadError.value = e instanceof Error ? e.message : '加载配置失败'
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function reload() {
    loaded.value = false
    loading.value = false
    await ensureLoaded()
  }

  function toolLabel(code: string | undefined): string {
    const raw = (code || '').trim()
    if (!raw) return '工具'
    const exact = toolDisplayNames.value[raw]
    if (exact) return exact
    const lower = raw.toLowerCase()
    for (const [k, v] of Object.entries(toolDisplayNames.value)) {
      if (k.toLowerCase() === lower && v) return v
    }
    const claude = claudeToolLabel(raw)
    if (claude) return claude
    if (raw.startsWith('mcp__')) {
      return `MCP · ${raw.slice('mcp__'.length).replace(/__/g, ' · ')}`
    }
    return raw
  }

  return {
    loaded,
    loading,
    loadError,
    disclaimer,
    greeting,
    capability,
    recommendLabel,
    portraitSeriesALabel,
    portraitSeriesBLabel,
    suggestedQuestions,
    toolDisplayNames,
    presetChat1,
    presetChat2,
    presetChat3,
    officerPortrait,
    systemName,
    claudeChatModel,
    claudeChatContextWindow,
    applySystemChrome,
    ensureLoaded,
    reload,
    toolLabel,
  }
})
