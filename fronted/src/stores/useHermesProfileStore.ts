import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listHermesProfiles, type HermesProfileItem } from '@/api/hermes'

const STORAGE_KEY = 'qianxun.hermesProfile'
/** 侧栏「数智干警」使用的 Hermes 内置 profile */
export const DEFAULT_HERMES_PROFILE = 'default'

export const useHermesProfileStore = defineStore('hermesProfile', () => {
  const profiles = ref<HermesProfileItem[]>([])
  const selectedProfile = ref('')
  const loading = ref(false)
  const loaded = ref(false)

  function persist(name: string) {
    selectedProfile.value = name
    if (name) localStorage.setItem(STORAGE_KEY, name)
    else localStorage.removeItem(STORAGE_KEY)
  }

  function setSelectedProfile(name: string) {
    persist((name || '').trim())
  }

  function syncFromAgent(profile?: string | null) {
    const p = (profile || '').trim()
    if (p) persist(p)
  }

  /** 数智干警入口：使用 Hermes 内置 default profile */
  function useDefaultProfile() {
    persist(DEFAULT_HERMES_PROFILE)
  }

  /** 当前选中（或默认 active）profile 配置的模型名 */
  function currentModelName(): string {
    const hit = currentProfile()
    return (hit?.model || '').trim()
  }

  function currentProfile(): HermesProfileItem | undefined {
    const name = (selectedProfile.value || '').trim()
    return name
      ? profiles.value.find((p) => p.name === name)
        || profiles.value.find((p) => p.name.toLowerCase() === name.toLowerCase())
      : profiles.value.find((p) => p.active) || profiles.value[0]
  }

  function currentContextWindow(): number {
    const w = currentProfile()?.contextWindow
    return w && w > 0 ? w : 0
  }

  function resolveAvailable(saved: string): string {
    const list = profiles.value
    if (saved) {
      const hit = list.find((p) => p.name === saved)
        || list.find((p) => p.name.toLowerCase() === saved.toLowerCase())
      if (hit) return hit.name
    }
    const active = list.find((p) => p.active)
    if (active) return active.name
    const def = list.find((p) => p.name === DEFAULT_HERMES_PROFILE)
    if (def) return def.name
    return list[0]?.name || DEFAULT_HERMES_PROFILE
  }

  async function refresh(force = false) {
    if (loading.value) return
    if (loaded.value && !force) return
    const silent = loaded.value && force
    if (!silent) loading.value = true
    try {
      profiles.value = await listHermesProfiles()
      loaded.value = true
      const saved = (selectedProfile.value || localStorage.getItem(STORAGE_KEY) || '').trim()
      persist(resolveAvailable(saved))
    } catch {
      if (!loaded.value) profiles.value = []
      if (!selectedProfile.value.trim()) persist(DEFAULT_HERMES_PROFILE)
    } finally {
      if (!silent) loading.value = false
    }
  }

  function resetToDefault() {
    profiles.value = []
    loaded.value = false
    persist(DEFAULT_HERMES_PROFILE)
  }

  return {
    profiles,
    selectedProfile,
    loading,
    loaded,
    setSelectedProfile,
    syncFromAgent,
    useDefaultProfile,
    currentModelName,
    currentContextWindow,
    currentProfile,
    refresh,
    resetToDefault,
  }
})
