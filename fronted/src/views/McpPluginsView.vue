<template>
  <div class="mcp-view">
    <header class="mcp-header">
      <div class="titles">
        <h1>MCP</h1>
        <p class="sub">{{ brandCopy.marketMcpSub }}</p>
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
        <a-button @click="loadServers">
          <template #icon><AppGlyph name="reload" size="sm" /></template>
          刷新
        </a-button>
        <a-button type="primary" :disabled="!profile" @click="openCreate">
          <template #icon><AppGlyph name="plus" size="sm" /></template>
          添加 MCP
        </a-button>
      </div>
    </header>

    <MarketSectionTabs section="mcp" />

    <a-alert
      v-if="gatewayStatus"
      type="info"
      show-icon
      class="runtime-hint"
      :message="gatewayStatus.configured
        ? `智能体运行时已连接（${displayGatewayModel}）· 已启用的 MCP 将在下次对话时加载`
        : '智能体运行时未配置或未连接'"
    />

    <a-input
      v-model:value="keyword"
      allow-clear
      placeholder="按名称、命令、描述搜索"
      class="search-bar"
    >
      <template #prefix><AppGlyph name="search" size="sm" /></template>
    </a-input>

    <section class="list-pane">
      <a-spin :spinning="loading">
        <div v-if="filteredServers.length" class="mcp-list">
          <article
            v-for="s in filteredServers"
            :key="s.name"
            class="mcp-card"
            :class="{ active: selected?.name === s.name, disabled: !s.enabled }"
            @click="openEdit(s)"
          >
            <div class="card-top">
              <span class="mcp-glyph"><AppGlyph name="tool" size="md" /></span>
              <div class="mcp-meta">
                <h2>{{ s.name }}</h2>
                <span class="state-text" :class="{ on: s.enabled }">{{ s.enabled ? '已启用' : '已停用' }}</span>
              </div>
              <a-switch
                size="small"
                :checked="s.enabled"
                @click.stop
                @change="(v: boolean) => onToggle(s, v)"
              />
            </div>
            <p class="desc">{{ s.description || s.command || '暂无描述' }}</p>
            <div class="facts">
              <a-tag>{{ s.transport || 'stdio' }}</a-tag>
              <span class="path" :title="s.command || s.url || ''">{{ s.command || s.url || '' }}</span>
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

    <a-modal
      v-model:open="formOpen"
      :title="editingName ? '编辑 MCP Server' : '添加 MCP Server'"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="saveForm"
    >
      <a-form layout="vertical" class="mcp-form">
        <a-form-item label="名称" required>
          <a-input
            v-model:value="form.name"
            :disabled="!!editingName"
            placeholder="例如 filesystem"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="传输方式">
          <a-select v-model:value="form.transport" :options="transportOptions" />
        </a-form-item>
        <a-form-item v-if="form.transport === 'stdio'" label="启动命令" required>
          <a-input v-model:value="form.command" placeholder="例如 npx -y @modelcontextprotocol/server-filesystem" allow-clear />
        </a-form-item>
        <a-form-item v-else label="服务地址" required>
          <a-input v-model:value="form.url" placeholder="https://…" allow-clear />
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="form.description" :rows="3" placeholder="可选，简述用途" allow-clear />
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
  deleteMcpServer,
  getGatewayStatus,
  listMcpServers,
  toggleMcpServer,
  upsertMcpServer,
  type GatewayStatus,
  type McpServerItem,
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
import { sanitizeUserFacingText } from '@/utils/userFacingCopy'

const profileStore = useHermesProfileStore()
const agents = ref<AgentRegistryItem[]>([])
const profilesLoading = ref(false)
const profile = ref('')
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const removing = ref(false)
const servers = ref<McpServerItem[]>([])
const selected = ref<McpServerItem | null>(null)
const gatewayStatus = ref<GatewayStatus | null>(null)
const formOpen = ref(false)
const editingName = ref('')
const form = ref({
  name: '',
  command: '',
  url: '',
  description: '',
  transport: 'stdio',
  enabled: true,
})

const transportOptions = [
  { label: 'stdio', value: 'stdio' },
  { label: 'http', value: 'http' },
  { label: 'sse', value: 'sse' },
]

const displayGatewayModel = computed(() =>
  sanitizeUserFacingText(gatewayStatus.value?.model || '默认模型'),
)

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

const filteredServers = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const list = k
    ? servers.value.filter((s) =>
        [s.name, s.command || '', s.url || '', s.description || '', s.transport || ''].join(' ').toLowerCase().includes(k),
      )
    : servers.value.slice()
  return list.sort((a, b) => {
    if (a.enabled !== b.enabled) return a.enabled ? -1 : 1
    return (a.name || '').localeCompare(b.name || '', 'zh-CN')
  })
})

const emptyHint = computed(() => {
  if (!profile.value) return '请先选择专业智能体'
  return '该专业智能体暂无 MCP Server，可添加启动命令或远程地址'
})

function resetForm() {
  form.value = { name: '', command: '', url: '', description: '', transport: 'stdio', enabled: true }
  editingName.value = ''
  selected.value = null
}

function openCreate() {
  resetForm()
  formOpen.value = true
}

function openEdit(s: McpServerItem) {
  selected.value = s
  editingName.value = s.name
  form.value = {
    name: s.name,
    command: s.command || '',
    url: s.url || '',
    description: s.description || '',
    transport: s.transport || 'stdio',
    enabled: s.enabled,
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

async function loadServers() {
  if (!profile.value) {
    servers.value = []
    selected.value = null
    return
  }
  loading.value = true
  try {
    const [list, status] = await Promise.all([
      listMcpServers(profile.value),
      getGatewayStatus().catch(() => null),
    ])
    servers.value = list
    gatewayStatus.value = status
    if (selected.value) {
      selected.value = servers.value.find((s) => s.name === selected.value?.name) || null
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 MCP 失败')
    servers.value = []
  } finally {
    loading.value = false
  }
}

async function onToggle(s: McpServerItem, enabled: boolean) {
  try {
    await toggleMcpServer(profile.value, s.name, enabled)
    s.enabled = enabled
    if (selected.value?.name === s.name) selected.value = { ...s, enabled }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '切换失败')
  }
}

async function saveForm() {
  const name = form.value.name.trim()
  const transport = form.value.transport || 'stdio'
  const command = form.value.command.trim()
  const url = form.value.url.trim()
  if (!profile.value) {
    message.warning('请先选择专业智能体')
    return Promise.reject()
  }
  if (!name) {
    message.warning('请填写名称')
    return Promise.reject()
  }
  if (transport === 'stdio' && !command) {
    message.warning('请填写启动命令')
    return Promise.reject()
  }
  if (transport !== 'stdio' && !url) {
    message.warning('请填写服务地址')
    return Promise.reject()
  }
  saving.value = true
  try {
    await upsertMcpServer({
      profile: profile.value,
      name,
      command,
      url,
      description: form.value.description.trim(),
      transport,
      enabled: form.value.enabled,
    })
    message.success(editingName.value ? 'MCP Server 已更新' : 'MCP Server 已添加')
    formOpen.value = false
    resetForm()
    await loadServers()
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
    title: '删除 MCP Server',
    content: `确定从当前专业智能体移除「${name}」？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      removing.value = true
      try {
        await deleteMcpServer(profile.value, name)
        message.success('已删除')
        formOpen.value = false
        resetForm()
        await loadServers()
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
  void loadServers()
})

onMounted(async () => {
  await loadProfiles()
  await loadServers()
})
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';

.mcp-view {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 28px;
  overflow: hidden;
  background: var(--bg-base);
}

.mcp-header {
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

.runtime-hint {
  margin-bottom: 12px;
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

.mcp-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.mcp-card {
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

  .mcp-glyph {
    width: 36px;
    height: 36px;
    border-radius: 11px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(118, 118, 128, 0.1);
    flex-shrink: 0;
  }

  .mcp-meta {
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

.mcp-form {
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
