import { computed } from 'vue'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { DEFAULT_BRAND_NAME } from '@/utils/brandCopy'

/** 读取当前系统设置名称（Pinia 未就绪时回退默认名） */
export function getSystemName(): string {
  try {
    const n = useBootstrapStore().systemName?.trim()
    if (n) return n
  } catch {
    /* pinia not active */
  }
  return DEFAULT_BRAND_NAME
}

/** 是否为默认智能体（系统设置名称 / 历史默认名）的展示名 */
export function isDigitalOfficerDisplayName(name?: string | null): boolean {
  const n = (name || '').trim()
  if (!n) return false
  if (n === DEFAULT_BRAND_NAME) return true
  return n === getSystemName()
}

/** 组件内响应式系统名称 */
export function useSystemName() {
  const bootstrap = useBootstrapStore()
  return computed(() => bootstrap.systemName?.trim() || DEFAULT_BRAND_NAME)
}
