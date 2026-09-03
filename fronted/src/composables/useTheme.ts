import { computed } from 'vue'
import { useThemeStore } from '@/stores/useThemeStore'

export function useTheme() {
  const themeStore = useThemeStore()

  const theme = computed(() => themeStore.theme)
  const isDark = computed(() => themeStore.isDark)

  /** 监听系统偏好变化（仅当用户未手动设置时自动跟随） */
  let mediaQuery: MediaQueryList | null = null
  let _listener: ((e: MediaQueryListEvent) => void) | null = null

  function initSystemPreferenceListener() {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    _listener = () => {
      // 仅在无 localStorage 记录时跟随系统
      const saved = localStorage.getItem('qianxun-theme')
      if (!saved || saved !== 'light' && saved !== 'dark') {
        themeStore.setTheme(mediaQuery!.matches ? 'dark' : 'light')
      }
    }
    mediaQuery.addEventListener('change', _listener)
  }

  return {
    theme,
    isDark,
    toggleTheme: themeStore.toggleTheme,
    initTheme: themeStore.initTheme,
    initSystemPreferenceListener,
  }
}
