<template>
  <div class="profile-selector">
    <span class="selector-label">专业智能体</span>
    <a-tooltip title="切换专业智能体（隔离配置 / 技能 / 记忆）" placement="topLeft">
      <a-select
        :value="profileStore.selectedProfile || undefined"
        class="sel"
        popup-class-name="toolbar-select-dropdown"
        allow-clear
        show-search
        :placeholder="systemName"
        :options="options"
        :loading="profileStore.loading"
        option-filter-prop="label"
        @change="onChange"
      />
    </a-tooltip>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useSystemName } from '@/utils/systemName'

const profileStore = useHermesProfileStore()
const agentContext = useAgentContextStore()
const systemName = useSystemName()

const options = computed(() =>
  profileStore.profiles.map((p) => {
    const label = agentContext.nameForProfile(p.name)
    return {
      label: p.active ? `${label}（当前）` : label,
      value: p.name,
    }
  }),
)

onMounted(() => {
  void agentContext.ensureAgents()
  void profileStore.refresh(false)
})

function onChange(value: string | undefined) {
  const next = (value || '').trim()
  if (next) {
    profileStore.setSelectedProfile(next)
    const agent = agentContext.agents.find((a) => (a.hermesProfile || '').trim() === next)
      || agentContext.agents.find((a) => a.code === next)
    if (agent) agentContext.setActiveAgent(agent)
    else agentContext.clearActiveAgent()
    return
  }
  // 数智干警入口清空选择时仍回落到 default
  profileStore.useDefaultProfile()
  agentContext.clearActiveAgent()
}
</script>

<style scoped lang="scss">
.profile-selector {
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

.sel {
  min-width: 168px;
  width: 168px;
  max-width: min(280px, 36vw);

  :deep(.ant-select-selector) {
    min-height: 32px !important;
    height: 32px !important;
    padding: 0 11px !important;
    border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.2)) !important;
    border-radius: 6px !important;
    background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58)) !important;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55) !important;
  }

  :deep(.ant-select-selection-search-input),
  :deep(.ant-select-selection-item),
  :deep(.ant-select-selection-placeholder) {
    line-height: 30px !important;
    font-size: var(--font-size-sm);
  }

  :deep(.ant-select-selection-item) {
    color: var(--text-secondary, #475569) !important;
  }

  :deep(.ant-select-selection-placeholder) {
    color: var(--text-muted, #94a3b8) !important;
  }

  :deep(.ant-select-arrow),
  :deep(.ant-select-clear) {
    color: var(--text-muted, #94a3b8);
  }
}
</style>

<style lang="scss">
.toolbar-select-dropdown {
  .ant-select-item {
    font-size: var(--font-size-sm);
    color: var(--text-secondary, #475569);
  }

  .ant-select-item-option-selected:not(.ant-select-item-option-disabled) {
    color: var(--text-primary, #1e293b);
    font-weight: var(--font-weight-medium);
  }
}
</style>
