<template>
  <div class="main-layout">
    <!-- 左侧导航栏 -->
    <SideNav class="sidebar" />

    <!-- 主内容区 -->
    <main class="main-content">
      <router-view />
    </main>

    <!-- 右侧网盘：智能体/技能/工具市场页隐藏 -->
    <RightPanel v-if="showRightPanel" class="right-panel" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserProfileStore } from '@/stores/useUserProfileStore'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import SideNav from '@/components/SideNav.vue'
import RightPanel from '@/components/RightPanel.vue'

const route = useRoute()
const userProfile = useUserProfileStore()
const bootstrap = useBootstrapStore()
const agentContext = useAgentContextStore()
const hermesProfileStore = useHermesProfileStore()

const showRightPanel = computed(() => {
  const name = String(route.name || '')
  if (name === 'market' || name === 'skill-market' || name === 'tool-market' || name === 'plugin-market' || name === 'mcp-market') return false
  const path = route.path || ''
  return !(path === '/market' || path.startsWith('/market/'))
})

onMounted(() => {
  void userProfile.ensureLoaded()
  void bootstrap.ensureLoaded()
  void agentContext.ensureAgents()
  if (!hermesProfileStore.selectedProfile.trim()) {
    hermesProfileStore.useDefaultProfile()
  }
  void hermesProfileStore.refresh(false)
})
</script>

<style scoped>
.main-layout {
  display: flex;
  width: 100%;
  height: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  overscroll-behavior: none;
  background-color: var(--bg-base, #f0f7ff);
  /* background-image: url(mainBg); 已关闭 */
  /* background-repeat: no-repeat;
  background-position: center center;
  background-size: cover; */
}

.sidebar {
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.main-content {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  width: auto;
  max-width: 100%;
  height: 100%;
  overflow: hidden;
  overflow-x: hidden;
  overscroll-behavior: none;
  position: relative;
}

.right-panel {
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  transition: width 0.22s ease;
}
</style>
