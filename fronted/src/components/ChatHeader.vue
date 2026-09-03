<template>
  <!-- background-image: url(topBg) 已关闭 -->
  <div class="chat-header">
    <div class="header-content">
      <router-link class="agent-entry" to="/market" title="查看专业智能体列表">
        <div class="avatar-wrapper">
          <AgentAvatar
            class="avatar"
            :group-key="chatAvatar.groupKey"
            :label="chatAvatar.label"
            :icon="chatAvatar.icon"
            size="md"
          />
        </div>
        <h1 class="title">{{ chatAgentTitle }}</h1>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AgentAvatar from '@/components/AgentAvatar.vue'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import { useChatAgentAvatar } from '@/composables/useChatAgentAvatar'
import { looksLikeTechnicalId } from '@/utils/agentDisplay'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { useSystemName } from '@/utils/systemName'

const agentContext = useAgentContextStore()
const hermesProfileStore = useHermesProfileStore()
const chatAvatar = useChatAgentAvatar()
const bootstrap = useBootstrapStore()
const systemName = useSystemName()
void bootstrap.ensureLoaded()

/** 优先注册表中文名；default / hermes-agent 显示系统名称 */
const chatAgentTitle = computed(() => {
  const name = agentContext.activeAgent?.name?.trim()
  if (name && !looksLikeTechnicalId(name)) return name
  const fromProfile = agentContext.nameForProfile(hermesProfileStore.selectedProfile)
  if (fromProfile && !looksLikeTechnicalId(fromProfile)) return fromProfile
  return systemName.value
})
</script>

<style scoped lang="scss">
.chat-header {
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--header-height, 56px);
  min-width: 0;
  max-width: 100%;
  padding: 0 20px;
  background-color: transparent;
  // background-image: url(topBg);
  // background-repeat: no-repeat;
  // background-position: center center;
  // background-size: cover;
  flex-shrink: 0;
  overflow: hidden;
  border-bottom: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
  box-shadow: 0 8px 24px rgba(15, 59, 110, 0.06);
  position: relative;
  z-index: 2;
  isolation: isolate;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(255, 255, 255, 0.22);
    pointer-events: none;
    z-index: 1;
  }

  .header-content {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    min-width: 0;
    max-width: 100%;
    position: relative;
    z-index: 2;

    .agent-entry {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
      min-width: 0;
      max-width: 100%;
      color: inherit;
      text-decoration: none;
      cursor: pointer;
      border-radius: 10px;
      padding: 4px 8px;

      &:hover {
        background: rgba(56, 102, 245, 0.08);

        .title {
          color: #3866f5;
        }
      }
    }

    .avatar-wrapper {
      display: flex;
      align-items: center;
      flex-shrink: 0;

      .avatar {
        width: 36px;
        height: 36px;
      }
    }

    .title {
      margin: 0;
      min-width: 0;
      font-size: var(--font-size-xl);
      font-weight: var(--font-weight-semibold);
      line-height: 1.25;
      color: var(--text-secondary);
      letter-spacing: 0.5px;
      font-family: var(--font-family-base);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}
</style>
