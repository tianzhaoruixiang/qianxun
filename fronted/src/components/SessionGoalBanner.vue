<template>
  <div v-if="goal" class="goal-banner">
    <AppGlyph name="goal" size="sm" />
    <div class="goal-banner-body">
      <div class="goal-banner-title">
        <span>当前目标</span>
        <strong>{{ goal.title || goal.description }}</strong>
      </div>
      <p v-if="goal.description && goal.description !== goal.title" class="goal-banner-desc">{{ goal.description }}</p>
      <p v-if="progressText" class="goal-banner-progress">{{ progressText }}</p>
    </div>
    <button type="button" class="goal-banner-clear" @click="$emit('clear')">清除目标</button>
  </div>
</template>

<script setup lang="ts">
import AppGlyph from '@/components/AppGlyph.vue'
import type { SessionGoal } from '@/utils/sessionGoal'

defineProps<{
  goal: SessionGoal | null
  progressText?: string
}>()

defineEmits<{
  clear: []
}>()
</script>

<style scoped lang="scss">
.goal-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 24px 8px;
  padding: 8px 12px;
  min-height: 40px;
  border-radius: 8px;
  border: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
  background: var(--chat-glass, rgba(255, 255, 255, 0.52));
  backdrop-filter: blur(8px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);

  > .ag-icon {
    flex-shrink: 0;
    margin-top: 0;
  }
}

.goal-banner-body {
  flex: 1;
  min-width: 0;
}

.goal-banner-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  line-height: 1.25;

  strong {
    font-size: var(--font-size-sm);
    color: var(--text-primary);
    font-weight: var(--font-weight-semibold);
    line-height: 1.25;
  }
}

.goal-banner-desc,
.goal-banner-progress {
  margin: 4px 0 0;
  min-height: 1.45em;
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  line-height: 1.45;
}

.goal-banner-clear {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 10px;
  border-radius: 6px;
  border: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
  background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58));
  color: var(--text-primary);
  font-size: var(--font-size-xs);
  line-height: 1;
  cursor: pointer;
}
</style>
