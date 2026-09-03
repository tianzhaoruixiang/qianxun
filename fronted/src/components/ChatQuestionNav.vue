<template>
  <aside class="question-nav" aria-label="本会话提问目录">
    <div class="nav-scroll" role="list">
      <button
        v-for="(item, index) in items"
        :key="item.id"
        type="button"
        class="nav-item"
        :class="{ active: item.id === activeId }"
        role="listitem"
        :title="item.full"
        :aria-label="`第 ${index + 1} 问：${item.full}`"
        @click="$emit('jump', item.id)"
      >
        {{ index + 1 }}
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
export interface QuestionNavItem {
  id: string
  full: string
}

defineProps<{
  items: QuestionNavItem[]
  activeId: string | null
}>()

defineEmits<{
  jump: [id: string]
}>()
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';

.question-nav {
  display: flex;
  flex-direction: column;
  flex: 0 0 28px;
  width: 28px;
  min-width: 28px;
  max-width: 28px;
  min-height: 0;
  margin: 8px 2px 8px 0;
  padding: 4px 0;
  background: transparent;
  box-sizing: border-box;
  overflow: hidden;
  opacity: 0.4;
  transition: opacity 0.18s ease;

  &:hover,
  &:focus-within {
    opacity: 1;
  }
}

.nav-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  @include hide-scrollbar;
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 22px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: var(--text-muted, #94a3b8);
  transition: color 0.15s ease, background 0.15s ease;

  &:hover {
    color: var(--text-primary);
    background: rgba(15, 23, 42, 0.05);
  }

  &.active {
    color: #3866f5;
    font-weight: 600;
  }
}

@include mobile {
  .question-nav {
    display: none;
  }
}
</style>
