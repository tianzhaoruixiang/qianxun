import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/** 左侧导航展开宽度（px） */
const SIDEBAR_WIDTH = 290
/** 右侧面板展开宽度（px） */
const RIGHT_PANEL_WIDTH = 430
/** 右侧面板收起后保留的窄条宽度（px） */
const RIGHT_PANEL_COLLAPSED_WIDTH = 40

const STORAGE_RIGHT = 'qianxun.rightPanelCollapsed'
const STORAGE_SIDEBAR = 'qianxun.sidebarCollapsed'

/**
 * 布局尺寸与左右面板折叠状态。
 */
export const useLayoutStore = defineStore('layout', () => {
  const sidebarCollapsed = ref(localStorage.getItem(STORAGE_SIDEBAR) === '1')
  const rightPanelCollapsed = ref(localStorage.getItem(STORAGE_RIGHT) !== '0')

  watch(sidebarCollapsed, (v) => {
    localStorage.setItem(STORAGE_SIDEBAR, v ? '1' : '0')
  })
  watch(rightPanelCollapsed, (v) => {
    localStorage.setItem(STORAGE_RIGHT, v ? '1' : '0')
  })

  const sidebarWidth = computed(() => (sidebarCollapsed.value ? 72 : SIDEBAR_WIDTH))
  const rightPanelWidth = computed(() =>
    rightPanelCollapsed.value ? RIGHT_PANEL_COLLAPSED_WIDTH : RIGHT_PANEL_WIDTH,
  )

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function toggleRightPanel() {
    rightPanelCollapsed.value = !rightPanelCollapsed.value
  }

  function setRightPanelCollapsed(v: boolean) {
    rightPanelCollapsed.value = v
  }

  return {
    sidebarCollapsed,
    rightPanelCollapsed,
    sidebarWidth,
    rightPanelWidth,
    toggleSidebar,
    toggleRightPanel,
    setRightPanelCollapsed,
  }
})
