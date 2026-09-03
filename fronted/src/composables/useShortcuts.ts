import { onMounted, onUnmounted, type ComputedRef } from 'vue'

export interface ShortcutBinding {
  /** 按键名称（如 'k', 'Escape', 'b'） */
  key: string
  /** 是否需要 Ctrl/Cmd 键 */
  ctrl?: boolean
  /** 是否需要 Shift 键 */
  shift?: boolean
  /** 触发时的处理函数 */
  handler: () => void
  /** 描述文字（用于帮助 UI） */
  description: string
  /** 可选激活条件 */
  enabled?: ComputedRef<boolean> | boolean
}

interface UseShortcutsOptions {
  /** 作用域：仅在指定元素内生效（默认 document） */
  target?: EventTarget | null
}

function matchShortcut(e: KeyboardEvent, binding: ShortcutBinding): boolean {
  // 跨平台兼容：Mac 用 Meta，其他用 Ctrl
  const ctrlExpected = binding.ctrl ?? false
  const shiftExpected = binding.shift ?? false
  const actualCtrl = e.ctrlKey || e.metaKey

  if (ctrlExpected !== actualCtrl) return false
  if (shiftExpected !== e.shiftKey) return false

  // 统一小写比较
  return e.key.toLowerCase() === binding.key.toLowerCase()
}

/**
 * 判断是否应跳过快捷键（在输入框/文本域中聚焦时）
 */
function shouldSkip(e: KeyboardEvent): boolean {
  const tag = (e.target as HTMLElement)?.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT'
}

/**
 * 快捷键注册 composable
 * - document 级别 keydown 事件委托
 * - 自动跳过 input/textarea 聚焦状态
 * - onUnmounted 自动清理
 * - 跨平台 Ctrl/Meta 兼容
 */
export function useShortcuts(
  bindings: ShortcutBinding[],
  options: UseShortcutsOptions = {},
): { unregister: () => void } {
  let cleanup: (() => void) | null = null

  function handleKeyDown(e: KeyboardEvent) {
    for (const binding of bindings) {
      // 检查是否启用
      const enabled = typeof binding.enabled === 'boolean'
        ? binding.enabled
        : binding.enabled?.value ?? true
      if (!enabled) continue

      if (matchShortcut(e, binding)) {
        // 非 Escape 且在输入框内时跳过
        if (binding.key !== 'Escape' && shouldSkip(e)) continue
        e.preventDefault()
        binding.handler()
        break
      }
    }
  }

  onMounted(() => {
    const target = options.target || document
    target.addEventListener('keydown', handleKeyDown as EventListener)
    cleanup = () => {
      target.removeEventListener('keydown', handleKeyDown as EventListener)
    }
  })

  onUnmounted(() => {
    cleanup?.()
  })

  return {
    unregister: () => cleanup?.(),
  }
}
