<template>
  <span class="agent-avatar" :class="[`size-${size}`, `tone-${tone}`]" :title="label" aria-hidden="true">
    <OfficerPortrait v-if="useClassicOfficer" :size="size" />
    <img v-else-if="photoSrc" :src="photoSrc" alt="" />
    <AgentPortrait v-else :icon="resolvedIcon" :seed="seed" :size="size" />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import userImg from '@/assets/images/portraits/user-officer.webp'
import AgentPortrait from '@/components/AgentPortrait.vue'
import OfficerPortrait from '@/components/OfficerPortrait.vue'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { DIGITAL_OFFICER_KEY, UNCATEGORIZED_AGENT_NAME } from '@/utils/agentDisplay'
import { isDigitalOfficerDisplayName } from '@/utils/systemName'
import { CLASSIC_OFFICER_PORTRAIT_ID, portraitIdFromGroupKey, resolveOfficerPortraitId } from '@/utils/agentPortraits'

const props = withDefaults(
  defineProps<{
    groupKey?: string
    label?: string
    icon?: string | null
    size?: 'sm' | 'md' | 'lg' | 'xl'
  }>(),
  { groupKey: '', label: '', icon: '', size: 'sm' },
)

const agentContext = useAgentContextStore()
const bootstrap = useBootstrapStore()

const isOfficer = computed(() => {
  const key = props.groupKey || ''
  if (key.startsWith('code:') || key.startsWith('profile:')) return false
  const name = (props.label || '').trim()
  return key === DIGITAL_OFFICER_KEY || isDigitalOfficerDisplayName(name)
})

const officerPortraitId = computed(() => resolveOfficerPortraitId(bootstrap.officerPortrait))

const useClassicOfficer = computed(
  () => isOfficer.value && officerPortraitId.value === CLASSIC_OFFICER_PORTRAIT_ID,
)

const isUncategorized = computed(() => {
  const key = props.groupKey || ''
  if (key.startsWith('code:')) return false
  return key === 'uncat' || (props.label || '').trim() === UNCATEGORIZED_AGENT_NAME
})

const photoSrc = computed(() => {
  if (isUncategorized.value) return userImg
  return ''
})

const resolvedIcon = computed(() => {
  if (isOfficer.value) return officerPortraitId.value
  if (props.icon) return props.icon
  return portraitIdFromGroupKey(props.groupKey || '', agentContext.agents)
})

const seed = computed(() => props.groupKey || props.label || '')

const tone = computed(() => {
  if (isOfficer.value) return 'officer'
  if (isUncategorized.value) return 'person'
  return 'portrait'
})
</script>

<style scoped lang="scss">
.agent-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 50%;
  background: transparent;

  &.size-sm {
    width: 26px;
    height: 26px;
  }

  &.size-md {
    width: 42px;
    height: 42px;
  }

  &.size-lg {
    width: 64px;
    height: 64px;
  }

  &.size-xl {
    width: 84px;
    height: 84px;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  :deep(.agent-portrait),
  :deep(.officer-portrait) {
    width: 100%;
    height: 100%;
  }

  &.tone-officer {
    background: #c5cdd6;
  }

  &.tone-person {
    background: #e5e7eb;
  }
}
</style>
