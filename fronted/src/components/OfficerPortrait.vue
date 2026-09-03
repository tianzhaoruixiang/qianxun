<template>
  <span class="officer-portrait" :class="[`size-${size}`]" aria-hidden="true">
    <img :src="photo" alt="" draggable="false" />
  </span>
</template>

<script setup lang="ts">
import photo from '@/assets/images/portraits/officer.webp'

withDefaults(
  defineProps<{
    size?: 'sm' | 'md' | 'lg' | 'xl'
  }>(),
  { size: 'md' },
)
</script>

<style scoped lang="scss">
.officer-portrait {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 50%;
  background: #c5cdd6;
  box-shadow:
    inset 0 0 0 1px rgba(15, 23, 42, 0.08),
    0 2px 6px rgba(16, 24, 32, 0.12);

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
