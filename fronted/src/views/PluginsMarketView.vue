<template>
  <div class="plugins-view">
    <header class="plugins-header">
      <div class="titles">
        <h1>插件</h1>
        <p class="sub">{{ brandCopy.marketPluginsSub }}</p>
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
        <a-button @click="loadPlugins">
          <template #icon><AppGlyph name="reload" size="sm" /></template>
          刷新
        </a-button>
        <a-button type="primary" :disabled="!profile" @click="openCreate">
          <template #icon><AppGlyph name="plus" size="sm" /></template>
          添加插件
        </a-button>
      </div>
    </header>

    <MarketSectionTabs section="plugins" />

    <a-input
      v-model:value="keyword"
      allow-clear
      placeholder="按名称、路径、描述搜索"
      class="search-bar"
    >
      <template #prefix><AppGlyph name="search" size="sm" /></template>
    </a-input>

    <section class="list-pane">
      <a-spin :spinning="loading">
        <div v-if="filteredPlugins.length" class="plugin-list">
          <article
            v-for="p in filteredPlugins"
            :key="p.name"
            class="plugin-card"
            :class="{ active: selected?.name === p.name, disabled: !p.enabled }"
            @click="openEdit(p)"
          >
            <div class="card-top">
              <span class="plugin-glyph"><AppGlyph name="plugin" size="md" /></span>
              <div class="plugin-meta">
                <h2>{{ p.name }}</h2>
                <span class="state-text" :class="{ on: p.enabled }">{{ p.enabled ? '已启用' : '已停用' }}</span>
              </div>
              <a-switch
                size="small"
                :checked="p.enabled"
                @click.stop
                @change="(v: boolean) => onToggle(p, v)"
              />
            </div>
            <p class="desc">{{ p.description || '暂无描述' }}</p>
            <div class="facts">
              <a-tag v-if="p.version">{{ p.version }}</a-tag>
              <span class="path" :title="p.path || ''">{{ p.path || '未指定路径' }}</span>
            </div>
          </article>
        </div>
        <a-empty v-else :description="emptyHint" class="empty">
          <template #image>
            <AppGlyph name="plugin" size="xl" class="empty-glyph" />
          </template>
        </a-empty>
      </a-spin>
    </section>

    <a-modal
      v-model:open="formOpen"
      :title="editingName ? '编辑插件' : '添加插件'"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="saveForm"
    >
      <a-form layout="vertical" class="plugin-form">
        <a-form-item label="名称" required>
          <a-input
            v-model:value="form.name"
            :disabled="!!editingName"
            placeholder="例如 my-plugin"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="路径" required>
          <a-input
            v-model:value="form.path"
            placeholder=".claude/plugins/my-plugin 或绝对路径"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="版本">
          <a-input v-model:value="form.version" placeholder="可选，如 1.0.0" allow-clear />
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="form.description" :rows="3" placeholder="可选，简述插件用途" allow-clear />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
      </a-form>
      <template v-if="editingName" #footer>
        <div class="modal-footer">
          <a-button danger :loading="removing" @click="confirmRemove">删除</a-button>
          <div class="footer-right">
            <a-button @click="formOpen = false">取消</a-button>
            <a-button type="primary" :loading="saving" @click="saveForm">保存</a-button>
          </div>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import MarketSectionTabs from '@/components/MarketSectionTabs.vue'
import AppGlyph from '@/components/AppGlyph.vue'
import {
  deletePlugin,
  listPlugins,
  togglePlugin,
  upsertPlugin,
  type PluginItem,
} from '@/api/mcp'
import { listRegistryAgents, type AgentRegistryItem } from '@/api/registry'
import { DEFAULT_HERMES_PROFILE, useHermesProfileStore } from '@/stores/useHermesProfileStore'
import {
  UNCATEGORIZED_AGENT_NAME,
  displayNameForHermesProfile,
  isDefaultHermesProfile,
} from '@/utils/agentDisplay'
import { getSystemName } from '@/utils/systemName'
import { brandCopy } from '@/utils/brandCopy'

const profileStore = useHermesProfileStore()
const agents = ref<AgentRegistryItem[]>([])
const profilesLoading = ref(false)
const profile = ref('')
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const removing = ref(false)
const plugins = ref<PluginItem[]>([])
const selected = ref<PluginItem | null>(null)
const formOpen = ref(false)
const editingName = ref('')
const form = ref({
  name: '',
  path: '',
  version: '',
  description: '',
  enabled: true,
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

const filteredPlugins = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const list = k
    ? plugins.value.filter((p) =>
        [p.name, p.path || '', p.version || '', p.description || ''].join(' ').toLowerCase().includes(k),
      )
    : plugins.value.slice()
  return list.sort((a, b) => {
    if (a.enabled !== b.enabled) return a.enabled ? -1 : 1
    return (a.name || '').localeCompare(b.name || '', 'zh-CN')
  })
})

const emptyHint = computed(() => {
  if (!profile.value) return '请先选择专业智能体'
  return '该专业智能体暂无插件，可添加本地插件目录'
})

function resetForm() {
  form.value = { name: '', path: '', version: '', description: '', enabled: true }
  editingName.value = ''
  selected.value = null
}

function openCreate() {
  resetForm()
  formOpen.value = true
}

function openEdit(p: PluginItem) {
  selected.value = p
  editingName.value = p.name
  form.value = {
    name: p.name,
    path: p.path || '',
    version: p.version || '',
    description: p.description || '',
    enabled: p.enabled,
  }
  formOpen.value = true
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

async function loadPlugins() {
  if (!profile.value) {
    plugins.value = []
    selected.value = null
    return
  }
  loading.value = true
  try {
    plugins.value = await listPlugins(profile.value)
    if (selected.value) {
      selected.value = plugins.value.find((p) => p.name === selected.value?.name) || null
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载插件失败')
    plugins.value = []
  } finally {
    loading.value = false
  }
}

async function onToggle(p: PluginItem, enabled: boolean) {
  try {
    await togglePlugin(profile.value, p.name, enabled)
    p.enabled = enabled
    if (selected.value?.name === p.name) selected.value = { ...p, enabled }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '切换失败')
  }
}

async function saveForm() {
  const name = form.value.name.trim()
  const path = form.value.path.trim()
  if (!profile.value) {
    message.warning('请先选择专业智能体')
    return Promise.reject()
  }
  if (!name) {
    message.warning('请填写插件名称')
    return Promise.reject()
  }
  if (!path) {
    message.warning('请填写插件路径')
    return Promise.reject()
  }
  saving.value = true
  try {
    await upsertPlugin({
      profile: profile.value,
      name,
      path,
      version: form.value.version.trim(),
      description: form.value.description.trim(),
      enabled: form.value.enabled,
    })
    message.success(editingName.value ? '插件已更新' : '插件已添加')
    formOpen.value = false
    resetForm()
    await loadPlugins()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function confirmRemove() {
  const name = editingName.value
  if (!name) return
  Modal.confirm({
    title: '删除插件',
    content: `确定从当前专业智能体移除「${name}」？仅取消登记，不会删除磁盘上的插件文件。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      removing.value = true
      try {
        await deletePlugin(profile.value, name)
        message.success('已删除')
        formOpen.value = false
        resetForm()
        await loadPlugins()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
        return Promise.reject(e)
      } finally {
        removing.value = false
      }
    },
  })
}

watch(profile, () => {
  resetForm()
  formOpen.value = false
  void loadPlugins()
})

onMounted(async () => {
  await loadProfiles()
  await loadPlugins()
})
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';

.plugins-view {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 28px;
  overflow: hidden;
  background: var(--bg-base);
}

.plugins-header {
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

.plugin-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.plugin-card {
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

  .plugin-glyph {
    width: 36px;
    height: 36px;
    border-radius: 11px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(13, 148, 136, 0.1);
    flex-shrink: 0;
  }

  .plugin-meta {
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
    min-width: 0;
  }

  .path {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.plugin-form {
  margin-top: 8px;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.footer-right {
  display: flex;
  gap: 8px;
}

.empty {
  margin: 48px 0;

  .empty-glyph {
    width: 42px;
    height: 42px;
  }
}
</style>
