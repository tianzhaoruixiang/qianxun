import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'qianxun-theme'
const DEFAULT_THEME: Theme = 'light'

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<Theme>(DEFAULT_THEME)

  const isDark = computed(() => theme.value === 'dark')

  /** 设置主题：更新 data-theme 属性 + localStorage 持久化 */
  function setTheme(newTheme: Theme) {
    if (newTheme !== 'light' && newTheme !== 'dark') return
    theme.value = newTheme
    document.documentElement.dataset.theme = newTheme
    try {
      localStorage.setItem(STORAGE_KEY, newTheme)
    } catch {
      // ignore
    }
  }

  /** 切换主题 */
  function toggleTheme() {
    setTheme(theme.value === 'light' ? 'dark' : 'light')
  }

  /** 初始化：localStorage → prefers-color-scheme → 默认 light */
  function initTheme() {
    let saved: Theme | null = null
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw === 'light' || raw === 'dark') saved = raw
    } catch {
      // ignore
    }
    if (!saved) {
      saved = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    }
    setTheme(saved)
  }

  return { theme, isDark, toggleTheme, setTheme, initTheme }
})
