<template>
  <div class="tools-view">
    <header class="tools-header">
      <div class="titles">
        <h1>工具市场</h1>
        <p class="sub">{{ brandCopy.marketToolsSub }}</p>
      </div>
      <div class="actions">
        <a-select
          v-model:value="profile"
          show-search
          allow-clear
          placeholder="选择专业智能体"
          class="profile-sel"
          :options="profileOptions"
          :loading="profilesLoading"
          option-filter-prop="label"
        />
        <a-button @click="loadToolsets">
          <template #icon><AppGlyph name="reload" size="sm" /></template>
          刷新
        </a-button>
      </div>
    </header>

    <MarketSectionTabs section="tools" />

    <a-input
      v-model:value="keyword"
      allow-clear
      placeholder="按名称、描述、工具搜索"
      class="search-bar"
    >
      <template #prefix><AppGlyph name="search" size="sm" /></template>
    </a-input>

    <section class="list-pane">
      <a-spin :spinning="loading">
        <div v-if="filteredToolsets.length" class="tool-list">
          <article
            v-for="t in filteredToolsets"
            :key="t.name"
            class="tool-card"
            :class="{ active: selected?.name === t.name, disabled: !isOn(t) }"
            @click="openDetail(t)"
          >
            <div class="card-top">
              <span class="set-glyph">
                <AppGlyph :name="toolsetGlyph(t)" size="md" />
              </span>
              <div class="set-meta">
                <h2>{{ t.label || t.name }}</h2>
                <span class="state-text" :class="{ on: isOn(t) }">{{ isOn(t) ? '已启用' : '已停用' }}</span>
              </div>
              <a-switch
                size="small"
                :checked="isOn(t)"
                @click.stop
                @change="(v: boolean) => onToggle(t, v)"
              />
            </div>
            <p class="desc">{{ t.description || '暂无描述' }}</p>
            <div class="facts">
              <a-tag v-if="t.platformLabel || t.platform">{{ t.platformLabel || t.platform }}</a-tag>
              <span>{{ (t.tools || []).length }} 个工具</span>
              <a-tag v-if="t.configured" color="green">已配置</a-tag>
              <a-tag v-else>未配置</a-tag>
            </div>
          </article>
        </div>
        <a-empty v-else :description="emptyHint" class="empty">
          <template #image>
            <AppGlyph name="tool" size="xl" class="empty-glyph" />
          </template>
        </a-empty>
      </a-spin>
    </section>

    <a-drawer
      v-model:open="detailOpen"
      placement="right"
      :width="drawerWidth"
      :mask="false"
      :keyboard="true"
      :title="selected ? (selected.label || selected.name) : '工具详情'"
      destroy-on-close
      root-class-name="tools-detail-drawer"
      @close="closeDetail"
    >
      <template v-if="selected">
        <div class="detail-head">
          <div>
            <p class="path-hint">{{ selected.name }}{{ selected.platform ? ` · ${selected.platformLabel || selected.platform}` : '' }}</p>
          </div>
          <div class="detail-status">
            <a-tag :color="isOn(selected) ? 'blue' : 'default'">{{ isOn(selected) ? '已启用' : '已停用' }}</a-tag>
            <a-switch
              size="small"
              :checked="isOn(selected)"
              @change="(v: boolean) => onToggle(selected, v)"
            />
          </div>
        </div>
        <p class="detail-desc">{{ selected.description || '暂无描述' }}</p>
        <h3 class="tools-title">包含工具</h3>
        <ul v-if="selected.tools?.length" class="tool-items">
          <li v-for="tool in selected.tools" :key="tool.name">
            <span class="item-glyph"><AppGlyph :name="toolItemGlyph(tool)" size="sm" /></span>
            <span>{{ tool.displayName || tool.name }}</span>
            <code v-if="tool.displayName && tool.displayName !== tool.name">{{ tool.name }}</code>
            <a-tag :color="toolOn(tool, selected) ? 'blue' : 'default'">{{ toolOn(tool, selected) ? '已启用' : '已停用' }}</a-tag>
          </li>
        </ul>
        <a-empty v-else description="该工具集未列出具体工具">
          <template #image>
            <AppGlyph name="tool" size="xl" class="empty-glyph" />
          </template>
        </a-empty>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import MarketSectionTabs from '@/components/MarketSectionTabs.vue'
import AppGlyph from '@/components/AppGlyph.vue'
import { listHermesToolsets, toggleHermesToolset, type HermesToolsetItem } from '@/api/hermes'
import { listRegistryAgents, type AgentRegistryItem } from '@/api/registry'
import { DEFAULT_HERMES_PROFILE, useHermesProfileStore } from '@/stores/useHermesProfileStore'
import {
  UNCATEGORIZED_AGENT_NAME,
  displayNameForHermesProfile,
  isDefaultHermesProfile,
} from '@/utils/agentDisplay'
import { getSystemName } from '@/utils/systemName'
import { brandCopy } from '@/utils/brandCopy'
import { toolKindToGlyph, resolveToolIconKind } from '@/utils/toolIcon'

const profileStore = useHermesProfileStore()
const agents = ref<AgentRegistryItem[]>([])
const profilesLoading = ref(false)
const profile = ref('')
const keyword = ref('')
const loading = ref(false)
const toolsets = ref<HermesToolsetItem[]>([])
const selected = ref<HermesToolsetItem | null>(null)
const detailOpen = ref(false)
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1200)

function onResize() {
  viewportWidth.value = window.innerWidth
}

const drawerWidth = computed(() => {
  const w = viewportWidth.value
  if (w <= 640) return '100%'
  if (w <= 960) return Math.min(420, Math.floor(w * 0.86))
  return 480
})

function profileDisplayName(name: string): string {
  const n = (name || '').trim()
  if (isDefaultHermesProfile(n)) return getSystemName()
  const fromAgent = displayNameForHermesProfile(n, agents.value)
  if (fromAgent && fromAgent !== UNCATEGORIZED_AGENT_NAME) return fromAgent
  return n
}

function profileOptionLabel(name: string, hint?: string): string {
  const base = profileDisplayName(name)
  return hint ? `${base}${hint}` : base
}

const profileOptions = computed(() => {
  const seen = new Set<string>()
  const opts: { label: string; value: string }[] = []
  const add = (value: string, label: string) => {
    if (!value || seen.has(value)) return
    seen.add(value)
    opts.push({ label, value })
  }
  for (const a of agents.value) {
    const p = (a.hermesProfile || '').trim()
    if (!p) continue
    add(p, a.name)
  }
  for (const p of profileStore.profiles) {
    if (!p.name) continue
    add(p.name, profileOptionLabel(p.name, p.active ? '（当前）' : ''))
  }
  if (!seen.has(DEFAULT_HERMES_PROFILE)) {
    opts.unshift({ label: profileOptionLabel(DEFAULT_HERMES_PROFILE), value: DEFAULT_HERMES_PROFILE })
  } else {
    const i = opts.findIndex((o) => o.value === DEFAULT_HERMES_PROFILE)
    if (i > 0) {
      const [d] = opts.splice(i, 1)
      opts.unshift(d)
    }
  }
  return opts
})

const filteredToolsets = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const list = k
    ? toolsets.value.filter((t) => {
        const tools = (t.tools || []).map((x) => `${x.name} ${x.displayName || ''}`).join(' ')
        return [t.name, t.label || '', t.description || '', t.platform || '', tools].join(' ').toLowerCase().includes(k)
      })
    : toolsets.value.slice()
  return list.sort((a, b) => {
    if (isOn(a) !== isOn(b)) return isOn(a) ? -1 : 1
    return (a.label || a.name || '').localeCompare(b.label || b.name || '', 'zh-CN')
  })
})

const emptyHint = computed(() => {
  if (!profile.value) return '请先选择专业智能体'
  return '未获取到工具集，请稍后重试'
})

function isOn(t: HermesToolsetItem | null | undefined): boolean {
  return t?.enabled === true
}

function toolOn(tool: { enabled?: boolean } | null | undefined, parent: HermesToolsetItem): boolean {
  return tool?.enabled === undefined ? isOn(parent) : tool.enabled === true
}

function openDetail(t: HermesToolsetItem) {
  selected.value = t
  detailOpen.value = true
}

function closeDetail() {
  detailOpen.value = false
  selected.value = null
}

function toolsetGlyph(t: HermesToolsetItem): string {
  const n = `${t.name} ${t.label || ''}`.toLowerCase()
  if (n.includes('browser') || n.includes('浏览器')) return 'browser'
  if (n.includes('terminal') || n.includes('终端') || n.includes('shell')) return 'terminal'
  if (n.includes('web') || n.includes('search') || n.includes('网页')) return 'web'
  if (n.includes('file') || n.includes('文件')) return 'folder'
  if (n.includes('code') || n.includes('代码') || n.includes('执行')) return 'analysis'
  if (n.includes('delegat') || n.includes('派工') || n.includes('子智能')) return 'agent'
  if (n.includes('memory') || n.includes('记忆')) return 'cluster'
  if (n.includes('todo') || n.includes('kanban') || n.includes('任务')) return 'grid'
  if (n.includes('vision') || n.includes('image') || n.includes('视觉') || n.includes('图片')) return 'preview'
  if (n.includes('desktop') || n.includes('computer') || n.includes('电脑')) return 'desktop'
  if (n.includes('skill') || n.includes('技能')) return 'skill'
  if (n.includes('plan') || n.includes('计划')) return 'analysis'
  return 'tool'
}

function toolItemGlyph(tool: { name?: string; iconKind?: string; displayName?: string }): string {
  const kind = resolveToolIconKind({
    iconKind: tool.iconKind,
    toolName: tool.name || tool.displayName || '',
  })
  return toolKindToGlyph(kind)
}

async function loadProfiles() {
  profilesLoading.value = true
  try {
    const [agentList] = await Promise.all([
      listRegistryAgents(false).catch(() => [] as AgentRegistryItem[]),
      profileStore.refresh(false),
    ])
    agents.value = agentList
    if (!profile.value) {
      profile.value =
        profileStore.selectedProfile
        || DEFAULT_HERMES_PROFILE
        || profileStore.profiles[0]?.name
        || agentList.find((a) => a.hermesProfile)?.hermesProfile?.trim()
        || ''
    }
  } finally {
    profilesLoading.value = false
  }
}

async function loadToolsets() {
  if (!profile.value) {
    toolsets.value = []
    selected.value = null
    detailOpen.value = false
    return
  }
  loading.value = true
  try {
    toolsets.value = await listHermesToolsets(profile.value)
    if (selected.value) {
      selected.value = toolsets.value.find((t) => t.name === selected.value?.name) || null
      if (!selected.value) detailOpen.value = false
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载工具集失败')
    toolsets.value = []
  } finally {
    loading.value = false
  }
}

async function onToggle(t: HermesToolsetItem | null, enabled: boolean) {
  if (!t) return
  try {
    const r = await toggleHermesToolset(profile.value, t.name, enabled)
    const next = r.enabled === true
    t.enabled = next
    if (t.tools) {
      t.tools = t.tools.map((tool) => ({ ...tool, enabled: next }))
    }
    if (selected.value?.name === t.name) {
      selected.value = { ...t, enabled: next, tools: t.tools }
    }
    await loadToolsets()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '切换失败')
  }
}

watch(profile, () => {
  closeDetail()
  void loadToolsets()
})

onMounted(async () => {
  window.addEventListener('resize', onResize)
  await loadProfiles()
  await loadToolsets()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';

.tools-view {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 28px;
  overflow: hidden;
  background: var(--bg-base);
}

.tools-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;

  .titles h1 {
    margin: 0 0 6px;
    font-size: var(--font-size-xl);
    font-weight: var(--font-weight-semibold);
    color: var(--text-primary);
  }

  .sub {
    margin: 0;
    font-size: var(--font-size-sm);
    color: var(--text-muted);
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
  }
}

.profile-sel {
  min-width: 220px;
}

.search-bar {
  max-width: 420px;
  margin-bottom: 16px;
}

.list-pane {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--card-bg);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 12px;
}

.tool-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.tool-card {
  @include market-card;
  @include market-disabled;
  padding: 16px;
  cursor: pointer;

  &.active {
    border-color: var(--card-border-hover, rgba(0, 0, 0, 0.1));
    box-shadow: var(--card-shadow-hover);
    background: var(--card-bg-solid, #fff);
  }

  h2 {
    margin: 0;
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    letter-spacing: var(--letter-spacing-tight);
    color: var(--text-primary);
  }

  .card-top {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    align-items: center;
  }

  .set-glyph {
    width: 36px;
    height: 36px;
    border-radius: 11px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(118, 118, 128, 0.1);
    flex-shrink: 0;
  }

  .set-meta {
    flex: 1;
    min-width: 0;
  }

  .state-text {
    display: block;
    margin-top: 2px;
    font-size: var(--font-size-xs);
    color: var(--text-muted);

    &.on {
      color: var(--color-accent);
    }
  }

  .desc {
    margin: 6px 0 0;
    font-size: var(--font-size-xs);
    color: var(--text-secondary);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .facts {
    margin-top: 8px;
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: var(--font-size-xs);
    color: var(--text-muted);
  }
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  align-items: flex-start;
  font-size: 12px;

  .path-hint {
    margin: 0;
    font-size: 12px;
    color: var(--text-muted);
  }

  .detail-status {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;

    :deep(.ant-tag) {
      font-size: 11px;
      line-height: 18px;
      padding-inline: 6px;
    }
  }
}

.detail-desc {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.55;
}

.tools-title {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: var(--font-weight-semibold);
}

.tool-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;

  li {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 12px;
    padding: 6px 8px;
    border: 1px solid var(--border-subtle);
    border-radius: 10px;
    background: var(--bg-elevated);

    .ant-tag {
      margin-inline-end: 0;
      margin-left: auto;
      font-size: 11px;
      line-height: 18px;
      padding-inline: 6px;
    }
  }

  .item-glyph {
    flex-shrink: 0;
    line-height: 0;
  }

  code {
    font-size: 11px;
    color: #3866f5;
  }
}

.empty {
  margin: 48px 0;

  .empty-glyph {
    width: 42px;
    height: 42px;
  }
}
</style>

<style lang="scss">
.tools-detail-drawer {
  /* 无遮罩：左侧列表可继续点选下一个工具集 */
  pointer-events: none;

  .ant-drawer-content-wrapper {
    pointer-events: auto;
    box-shadow:
      -8px 0 28px rgba(15, 23, 42, 0.08),
      -1px 0 0 rgba(148, 163, 184, 0.18);
  }

  .ant-drawer-header-title .ant-drawer-title {
    font-size: 14px;
    font-weight: 600;
    line-height: 1.35;
  }

  .ant-drawer-body {
    font-size: 12px;
  }
}
</style>
