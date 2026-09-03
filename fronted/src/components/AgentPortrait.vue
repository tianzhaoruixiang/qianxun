<template>
  <span class="agent-portrait" :class="[`size-${size}`]" aria-hidden="true">
    <img :src="preset.photo" alt="" draggable="false" />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getPortraitPreset, portraitIdForAgent } from '@/utils/agentPortraits'

const props = withDefaults(
  defineProps<{
    icon?: string | null
    seed?: string | null
    size?: 'sm' | 'md' | 'lg' | 'xl'
  }>(),
  { icon: '', seed: '', size: 'md' },
)

const preset = computed(() => getPortraitPreset(portraitIdForAgent(props.icon, props.seed)))
</script>

<style scoped lang="scss">
.agent-portrait {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 50%;
  background: #d7dde5;
  box-shadow:
    inset 0 0 0 1px rgba(15, 23, 42, 0.08),
    0 2px 6px rgba(15, 23, 42, 0.1);

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
    object-position: center 22%;
    display: block;
  }
}
</style>
