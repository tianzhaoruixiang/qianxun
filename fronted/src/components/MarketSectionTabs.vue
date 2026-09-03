<template>
  <div class="market-tabs">
    <router-link class="tab" :class="{ active: section === 'agents' }" :to="{ name: 'market' }">
      <AppGlyph name="agent" size="sm" class="tab-icon" />
      专业智能体
    </router-link>
    <router-link class="tab" :class="{ active: section === 'skills' }" :to="{ name: 'skill-market' }">
      <AppGlyph name="skill" size="sm" class="tab-icon" />
      技能市场
    </router-link>
    <router-link class="tab" :class="{ active: section === 'tools' }" :to="{ name: 'tool-market' }">
      <AppGlyph name="tool" size="sm" class="tab-icon" />
      工具市场
    </router-link>
    <router-link class="tab" :class="{ active: section === 'plugins' }" :to="{ name: 'plugin-market' }">
      <AppGlyph name="plugin" size="sm" class="tab-icon" />
      插件
    </router-link>
    <router-link class="tab" :class="{ active: section === 'mcp' }" :to="{ name: 'mcp-market' }">
      <AppGlyph name="tool" size="sm" class="tab-icon" />
      MCP
    </router-link>
  </div>
</template>

<script setup lang="ts">
import AppGlyph from '@/components/AppGlyph.vue'

defineProps<{ section?: 'agents' | 'skills' | 'tools' | 'plugins' | 'mcp' }>()
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';

.market-tabs {
  display: flex;
  gap: 2px;
  margin-bottom: 18px;
  padding: 3px;
  width: fit-content;
  background: rgba(118, 118, 128, 0.12);
  border-radius: 12px;
  border: none;
}

.tab {
  @include icon-text-btn(6px);
  padding: 7px 14px;
  min-height: 32px;
  border-radius: 10px;
  font-size: var(--font-size-sm);
  color: var(--nav-text-secondary);
  text-decoration: none;
  transition: background 0.22s ease, color 0.22s ease, box-shadow 0.22s ease;

  &:hover {
    color: var(--text-primary);
    background: rgba(255, 255, 255, 0.45);
  }

  &.active {
    background: var(--card-bg-solid, #fff);
    color: var(--text-primary);
    font-weight: var(--font-weight-semibold);
    box-shadow: var(--shadow-sm);
  }
}
</style>
