import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { listRegistryModels } from '@/api/registry'
import type { AgentRegistryItem, ModelRegistryItem } from '@/api/registry'
import type { ModelOption } from '@/types/chat'

const STORAGE_MODEL = 'qianxunSelectedModel'
/** 与后端 Hermes 默认路由一致，优先于注册表列表排序 */
const PREFERRED_DEFAULT_MODEL_CODES = ['hermes-agent', 'qianxun-default']

/**
 * 模型选项来自后端 model_registry；选择写回 localStorage。
 */
export const useKnowledgeStore = defineStore('knowledge', () => {
  const models = ref<ModelOption[]>([])
  const modelRegistryList = ref<ModelRegistryItem[]>([])
  const selectedModel = ref('')
  const loaded = ref(false)
  const modelsLoading = ref(false)

  watch(selectedModel, (v) => {
    if (v) localStorage.setItem(STORAGE_MODEL, v)
    else localStorage.removeItem(STORAGE_MODEL)
  })

  function pickDefaultModelCode(): string {
    for (const code of PREFERRED_DEFAULT_MODEL_CODES) {
      if (models.value.some((m) => m.value === code)) return code
    }
    return ''
  }

  function applyPersistedModelSelection() {
    const saved = localStorage.getItem(STORAGE_MODEL)
    if (saved && models.value.some((m) => m.value === saved)) {
      selectedModel.value = saved
      return
    }
    const preferred = pickDefaultModelCode()
    if (!selectedModel.value) {
      selectedModel.value = preferred
    }
    if (selectedModel.value && !models.value.some((m) => m.value === selectedModel.value)) {
      selectedModel.value = preferred
    }
  }

  /**
   * 根据当前选中的智能体（注册表）同步「模型」下拉：
   * - 有注册默认 modelCode 且在列表中 → 选中该项（用户可改）
   * - 有 modelCode 但不在列表中 → 仍写入 code（请求携带；下拉可能无匹配项）
   * - 否则 → 恢复本地持久化的全局模型偏好
   */
  function syncSelectionForActiveAgent(agent: AgentRegistryItem | null) {
    if (!agent) {
      applyPersistedModelSelection()
      return
    }
    const mc = agent.modelCode?.trim() ?? ''
    if (mc) {
      selectedModel.value = mc
      return
    }
    applyPersistedModelSelection()
  }

  async function loadRegistry(force = false) {
    if (!force && loaded.value) return
    modelsLoading.value = true
    try {
      const ml = await listRegistryModels(true)
      modelRegistryList.value = ml
      models.value = ml.map((m) => ({
        label: formatModelLabel(m),
        value: m.code,
      }))
      applyPersistedModelSelection()
    } catch {
      modelRegistryList.value = []
      models.value = []
    } finally {
      loaded.value = true
      modelsLoading.value = false
    }
  }

  function formatModelLabel(m: ModelRegistryItem): string {
    const name = (m.name || '').trim()
    return name || (m.code || '').trim()
  }

  async function ensureLoaded() {
    await loadRegistry(false)
  }

  async function refreshRegistry() {
    loaded.value = false
    await loadRegistry(true)
  }

  return {
    models,
    modelRegistryList,
    selectedModel,
    loaded,
    modelsLoading,
    ensureLoaded,
    refreshRegistry,
    syncSelectionForActiveAgent,
  }
})
