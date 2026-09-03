<template>
  <div class="model-selector">
    <span class="selector-label">模型</span>
    <a-tooltip :title="displayTooltip" placement="topLeft">
      <button
        v-if="userProfile.isAdmin"
        type="button"
        class="toolbar-chip clickable"
        @click="settingsOpen = true"
      >
        <span class="chip-name">{{ displayLabel }}</span>
        <span v-if="windowLabel" class="chip-window">{{ windowLabel }}</span>
      </button>
      <div v-else class="toolbar-chip" :class="{ muted: !displayLabel || displayLabel === '—' }">
        <span class="chip-name">{{ displayLabel }}</span>
        <span v-if="windowLabel" class="chip-window">{{ windowLabel }}</span>
      </div>
    </a-tooltip>
    <SystemSettingsModal v-if="userProfile.isAdmin" v-model:open="settingsOpen" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { useKnowledgeStore } from '@/stores/useKnowledgeStore'
import { useUserProfileStore } from '@/stores/useUserProfileStore'
import { useResolvedContextWindow } from '@/composables/useResolvedContextWindow'
import SystemSettingsModal from '@/components/SystemSettingsModal.vue'
import { formatTokenCount } from '@/utils/contextUsage'

const bootstrap = useBootstrapStore()
const knowledge = useKnowledgeStore()
const userProfile = useUserProfileStore()
const { resolvedContextWindow } = useResolvedContextWindow()
const settingsOpen = ref(false)

const displayLabel = computed(() => {
  const code = (bootstrap.claudeChatModel || '').trim()
  if (!code) return knowledge.modelsLoading ? '加载中…' : '—'
  const hit = knowledge.modelRegistryList.find((m) => m.code === code)
  if (hit?.name?.trim()) return hit.name.trim()
  return code
})

const contextWindow = computed(() => resolvedContextWindow.value)

const windowLabel = computed(() => {
  const w = contextWindow.value
  return w > 0 ? `最大 ${formatTokenCount(w)}` : ''
})

const displayTooltip = computed(() => {
  const code = (bootstrap.claudeChatModel || '').trim()
  if (!code) return '尚未加载当前模型'
  const extra = userProfile.isAdmin ? '。点击可配置' : ''
  const win = contextWindow.value > 0 ? `，最大上下文 ${formatTokenCount(contextWindow.value)}` : ''
  return `当前模型：${code}${win}${extra}`
})

onMounted(() => {
  void bootstrap.ensureLoaded()
  void knowledge.ensureLoaded()
  void userProfile.ensureLoaded()
})
</script>

<style scoped lang="scss">
.model-selector {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.selector-label {
  font-size: var(--font-size-xs);
  color: var(--text-muted, #94a3b8);
  flex-shrink: 0;
  line-height: 1.25;
  display: inline-flex;
  align-items: center;
  height: 32px;
}

.toolbar-chip {
  min-width: 168px;
  width: 220px;
  max-width: min(360px, 48vw);
  height: 32px;
  padding: 0 11px;
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.2));
  border-radius: 6px;
  background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
  color: var(--text-secondary, #475569);
  font-size: var(--font-size-sm);
  line-height: 1;
  cursor: default;
  overflow: hidden;
  gap: 6px;

  &.muted {
    color: var(--text-muted, #94a3b8);
  }

  &.clickable {
    cursor: pointer;
    text-align: left;

    &:hover {
      border-color: rgba(56, 102, 245, 0.28);
      color: #3866f5;
    }
  }
}

.chip-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-window {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--text-muted, #94a3b8);
}
</style>
