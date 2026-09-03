<template>
  <div class="market-view">
    <header class="market-header">
      <div class="titles">
        <h1>专业智能体</h1>
        <p class="sub">{{ userProfile.isAdmin ? brandCopy.marketAgentsSub : brandCopy.spaceQuickMarketDesc }}</p>
      </div>
      <div class="actions">
        <a-switch
          v-if="userProfile.isAdmin"
          v-model:checked="enabledOnly"
          checked-children="仅启用"
          un-checked-children="全部"
        />
        <a-button @click="loadAgents">
          <template #icon><AppGlyph name="reload" size="sm" /></template>
          刷新
        </a-button>
        <a-button v-if="userProfile.isAdmin" type="primary" @click="openRegisterModal">
          <template #icon><AppGlyph name="plus" size="sm" /></template>
          注册专业智能体
        </a-button>
      </div>
    </header>

    <MarketSectionTabs v-if="userProfile.isAdmin" section="agents" />

    <a-input
      v-model:value="keyword"
      allow-clear
      placeholder="按名称、编码、描述搜索"
      class="search-bar"
    >
      <template #prefix><AppGlyph name="search" size="sm" /></template>
    </a-input>

    <a-spin :spinning="loading">
      <div v-if="officerVisible || filteredAgents.length" class="agent-grid">
        <article v-if="officerVisible" class="agent-card">
          <div class="card-hero">
            <AgentAvatar :group-key="DIGITAL_OFFICER_KEY" :label="systemName" size="lg" />
            <div class="meta">
              <div class="name-row">
                <h2 class="name">{{ systemName }}</h2>
                <span class="status on">系统默认</span>
              </div>
              <p class="desc">{{ brandCopy.welcomeCapability }}</p>
            </div>
          </div>
          <div class="card-actions">
            <a-button v-if="userProfile.isAdmin" type="link" size="small" @click="openOfficerPresetModal">
              修改预置对话
            </a-button>
            <a-button type="primary" size="small" @click="goOfficerChat">进入对话</a-button>
          </div>
        </article>
        <article v-for="a in filteredAgents" :key="a.id" class="agent-card" :class="{ disabled: !a.enabled }">
          <div class="card-hero">
            <AgentPortrait :icon="a.icon" :seed="a.code" size="lg" />
            <div class="meta">
              <div class="name-row">
                <h2 class="name">{{ a.name }}</h2>
                <span class="status" :class="{ on: a.enabled }">{{ a.enabled ? '已启用' : '已停用' }}</span>
              </div>
              <p class="desc">{{ a.description || '暂无描述' }}</p>
              <p v-if="userProfile.isAdmin && boundAgentLabel(a)" class="bind">绑定 {{ boundAgentLabel(a) }}</p>
            </div>
          </div>
          <div class="card-actions">
            <a-button
              v-if="userProfile.isAdmin"
              type="link"
              danger
              size="small"
              class="btn-delete"
              @click="confirmDeleteAgent(a)"
            >
              删除
            </a-button>
            <a-button v-if="userProfile.isAdmin" type="link" size="small" @click="openEditModal(a)">修改</a-button>
            <a-button type="primary" size="small" :disabled="!a.enabled" @click="goChat(a)">
              进入对话
            </a-button>
          </div>
        </article>
      </div>
      <a-empty v-else :description="userProfile.isAdmin ? '没有匹配的专业智能体' : '暂无可用专业智能体'" class="empty">
        <template #image>
          <AppGlyph name="agent" size="xl" class="empty-icon" />
        </template>
      </a-empty>
    </a-spin>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '修改专业智能体' : '注册专业智能体'"
      :confirm-loading="submitting"
      width="720px"
      wrap-class-name="agent-register-modal"
      destroy-on-close
      @ok="onModalOk"
    >
      <a-form layout="vertical" class="agent-form" :model="form">
        <a-form-item label="显示名称" required>
          <a-input v-model:value="form.name" placeholder="专业智能体名称" />
        </a-form-item>
        <a-form-item label="编码（唯一标识）" required>
          <a-input
            v-model:value="form.code"
            placeholder="如 my_agent，仅字母数字下划线连字符"
            :disabled="editing"
          />
        </a-form-item>
        <a-form-item label="灵魂 SOUL.md" required>
          <a-textarea
            v-model:value="form.soulMd"
            :rows="8"
            :maxlength="4000"
            show-count
            placeholder="# Soul&#10;&#10;你是该智能体的人格与原则。例如：&#10;- 用简洁中文回答&#10;- 不确定时先说明假设"
          />
          <span class="hint">写入该智能体的灵魂文件 SOUL.md，定义人格、原则与行为边界。</span>
        </a-form-item>
        <a-form-item label="分类">
          <a-select v-model:value="form.category" :options="categoryOptions" />
        </a-form-item>
        <a-form-item label="简介">
          <a-textarea v-model:value="form.description" :rows="3" placeholder="一句话说明能力与应用场景" />
        </a-form-item>
        <a-divider orientation="left">对话欢迎语（可选）</a-divider>
        <p class="form-section-hint">
          从本智能体进入对话时空态展示；留空则主标题为「你好，我是 {名称}」，简介沿用全局默认。
        </p>
        <a-form-item label="欢迎主标题">
          <a-input v-model:value="form.welcomeTitle" placeholder="例如：你好，我是某某助手" />
        </a-form-item>
        <a-form-item label="欢迎简介">
          <a-textarea v-model:value="form.welcomeIntro" :rows="3" placeholder="空态下的说明文案" />
        </a-form-item>
        <a-divider orientation="left">预置对话（可选）</a-divider>
        <p class="form-section-hint">
          最多三条。用户从对话欢迎页点击后将进入对话并自动发送对应内容。
        </p>
        <a-form-item label="预置对话 1">
          <a-textarea v-model:value="form.presetChat1" :rows="2" placeholder="例如：请帮我总结本周行业要闻" />
        </a-form-item>
        <a-form-item label="预置对话 2">
          <a-textarea v-model:value="form.presetChat2" :rows="2" />
        </a-form-item>
        <a-form-item label="预置对话 3">
          <a-textarea v-model:value="form.presetChat3" :rows="2" />
        </a-form-item>
        <a-form-item label="头像">
          <div class="icon-picks">
            <button
              v-for="opt in AGENT_PORTRAITS"
              :key="opt.id"
              type="button"
              class="icon-pick"
              :class="{ active: form.icon === opt.id }"
              :title="opt.hint"
              @click="form.icon = opt.id"
            >
              <AgentPortrait :icon="opt.id" size="md" />
              <span>{{ opt.label }}</span>
            </button>
          </div>
        </a-form-item>
        <a-divider orientation="left">专业智能体</a-divider>
        <p class="form-section-hint">
          每个条目绑定独立智能体（隔离配置、技能与记忆）。保存时会自动创建对应智能体配置（已存在则复用，名称默认用编码）。对话一律走该智能体。
        </p>
        <a-form-item label="排序优先级">
          <a-input-number v-model:value="form.priority" :min="0" :max="9999" style="width: 100%" />
          <span class="hint">数值越小越靠前</span>
        </a-form-item>
        <a-form-item label="启用">
          <div class="enable-row">
            <a-switch v-model:checked="form.enabled" checked-children="开" un-checked-children="关" />
            <span class="enable-hint">{{ form.enabled ? '启用后可进入对话' : '停用后不可进入对话' }}</span>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="officerModalOpen"
      :title="`${systemName} · 预置对话`"
      :confirm-loading="officerSubmitting"
      width="720px"
      destroy-on-close
      @ok="onOfficerModalOk"
    >
      <p class="form-section-hint">最多三条。用户从{{ systemName }}欢迎页点击后将进入对话并自动发送对应内容。留空的条目不展示。</p>
      <a-form layout="vertical" class="agent-form">
        <a-form-item label="预置对话 1">
          <a-textarea v-model:value="officerForm.presetChat1" :rows="2" :maxlength="2000" show-count />
        </a-form-item>
        <a-form-item label="预置对话 2">
          <a-textarea v-model:value="officerForm.presetChat2" :rows="2" :maxlength="2000" show-count />
        </a-form-item>
        <a-form-item label="预置对话 3">
          <a-textarea v-model:value="officerForm.presetChat3" :rows="2" :maxlength="2000" show-count />
        </a-form-item>
        <a-form-item label="头像">
          <div class="icon-picks">
            <button
              v-for="opt in OFFICER_PORTRAIT_CHOICES"
              :key="opt.id"
              type="button"
              class="icon-pick"
              :class="{ active: officerForm.officerPortrait === opt.id }"
              :title="opt.hint"
              @click="officerForm.officerPortrait = opt.id"
            >
              <OfficerPortrait v-if="opt.id === CLASSIC_OFFICER_PORTRAIT_ID" size="md" />
              <AgentPortrait v-else :icon="opt.id" size="md" />
              <span>{{ opt.label }}</span>
            </button>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import AgentAvatar from '@/components/AgentAvatar.vue'
import AgentPortrait from '@/components/AgentPortrait.vue'
import OfficerPortrait from '@/components/OfficerPortrait.vue'
import AppGlyph from '@/components/AppGlyph.vue'
import type { AgentRegistryItem, ModelRegistryItem, UpsertAgentPayload } from '@/api/registry'
import { deleteRegistryAgent, listRegistryAgents, listRegistryModels, upsertRegistryAgent } from '@/api/registry'
import { getHermesSoul } from '@/api/hermes'
import { fetchWelcomePresets, updateWelcomePresets } from '@/api/welcome'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useChatStore } from '@/stores/useChatStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import { useUserProfileStore } from '@/stores/useUserProfileStore'
import MarketSectionTabs from '@/components/MarketSectionTabs.vue'
import {
  DIGITAL_OFFICER_KEY,
  displayNameForHermesProfile,
  looksLikeTechnicalId,
  UNCATEGORIZED_AGENT_NAME,
} from '@/utils/agentDisplay'
import { DEFAULT_BRAND_NAME } from '@/utils/brandCopy'
import { useSystemName } from '@/utils/systemName'
import { AGENT_PORTRAITS, CLASSIC_OFFICER_PORTRAIT_ID, DEFAULT_PORTRAIT_ID, OFFICER_PORTRAIT_CHOICES, portraitIdForAgent, resolveOfficerPortraitId } from '@/utils/agentPortraits'
import { brandCopy } from '@/utils/brandCopy'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
const router = useRouter()
const agentContext = useAgentContextStore()
const chatStore = useChatStore()
const userProfile = useUserProfileStore()
const bootstrap = useBootstrapStore()
const systemName = useSystemName()

const loading = ref(false)
const agents = ref<AgentRegistryItem[]>([])
const keyword = ref('')
const enabledOnly = ref(false)

const modalOpen = ref(false)
const editing = ref(false)
const submitting = ref(false)
const officerModalOpen = ref(false)
const officerSubmitting = ref(false)
const officerForm = reactive({
  presetChat1: '',
  presetChat2: '',
  presetChat3: '',
  officerPortrait: CLASSIC_OFFICER_PORTRAIT_ID,
})

/** model_registry.code → 展示名称，用于搜索匹配历史 modelCode */
const modelNameByCode = ref<Record<string, string>>({})

const form = reactive({
  code: '',
  name: '',
  category: 'general',
  description: '',
  icon: DEFAULT_PORTRAIT_ID,
  welcomeTitle: '',
  welcomeIntro: '',
  presetChat1: '',
  presetChat2: '',
  presetChat3: '',
  priority: 100,
  enabled: true,
  hermesProfile: '',
  soulMd: '',
})

const categoryOptions = [
  { label: '通用', value: 'general' },
  { label: '助手', value: 'assistant' },
  { label: '分析', value: 'analysis' },
  { label: '目录', value: 'market' },
]

const officerVisible = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return true
  const blob = [
    systemName.value,
    DEFAULT_BRAND_NAME,
    '默认',
    '系统默认',
    brandCopy.welcomeCapability,
    officerForm.presetChat1,
    officerForm.presetChat2,
    officerForm.presetChat3,
    bootstrap.presetChat1,
    bootstrap.presetChat2,
    bootstrap.presetChat3,
  ]
    .join(' ')
    .toLowerCase()
  return blob.includes(k)
})

const filteredAgents = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return agents.value
  return agents.value.filter((a) => {
    const blob = [
      a.name,
      a.code,
      a.description || '',
      a.category,
      agentDisplayModel(a),
      a.modelCode || '',
      a.presetChat1 || '',
      a.presetChat2 || '',
      a.presetChat3 || '',
      displayNameForHermesProfile(a.hermesProfile || '', agents.value),
    ]
      .join(' ')
      .toLowerCase()
    return blob.includes(k)
  })
})

function boundAgentLabel(a: AgentRegistryItem): string {
  const p = (a.hermesProfile || '').trim()
  if (!p) return ''
  const n = displayNameForHermesProfile(p, agents.value)
  if (n && n !== UNCATEGORIZED_AGENT_NAME && !looksLikeTechnicalId(n)) return n
  const self = (a.name || '').trim()
  if (self && !looksLikeTechnicalId(self)) return self
  return ''
}

function syncModelRegistryFromList(list: ModelRegistryItem[]) {
  const map: Record<string, string> = {}
  for (const m of list) {
    map[m.code] = m.name
  }
  modelNameByCode.value = map
}

/** 搜索用：优先模型注册表 display name */
function agentDisplayModel(a: AgentRegistryItem): string {
  const code = a.modelCode?.trim()
  if (!code) return ''
  return modelNameByCode.value[code] || code
}

async function loadAgents() {
  loading.value = true
  try {
    const [agentList, modelList] = await Promise.all([
      listRegistryAgents(userProfile.isAdmin ? enabledOnly.value : true),
      listRegistryModels(false).catch(() => [] as ModelRegistryItem[]),
    ])
    agents.value = agentList
    syncModelRegistryFromList(modelList)
    if (enabledOnly.value) {
      void agentContext.ensureAgents(true)
    } else {
      agentContext.replaceAgents(agentList)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载专业智能体列表失败')
    agents.value = []
  } finally {
    loading.value = false
  }
}

watch(enabledOnly, () => {
  void loadAgents()
})

function resetForm() {
  form.code = ''
  form.name = ''
  form.category = 'general'
  form.description = ''
  form.icon = DEFAULT_PORTRAIT_ID
  form.welcomeTitle = ''
  form.welcomeIntro = ''
  form.presetChat1 = ''
  form.presetChat2 = ''
  form.presetChat3 = ''
  form.priority = 100
  form.enabled = true
  form.hermesProfile = ''
  form.soulMd = ''
}

function openRegisterModal() {
  editing.value = false
  soulLoadPromise = null
  resetForm()
  modalOpen.value = true
}

function openEditModal(a: AgentRegistryItem) {
  editing.value = true
  form.code = a.code
  form.name = a.name
  form.category = a.category || 'general'
  form.description = a.description || ''
  form.icon = portraitIdForAgent(a.icon, a.code)
  form.welcomeTitle = a.welcomeTitle || ''
  form.welcomeIntro = a.welcomeIntro || ''
  form.presetChat1 = a.presetChat1 || ''
  form.presetChat2 = a.presetChat2 || ''
  form.presetChat3 = a.presetChat3 || ''
  form.priority = a.priority
  form.enabled = a.enabled
  form.hermesProfile = a.hermesProfile || ''
  form.soulMd = ''
  modalOpen.value = true
  soulLoadPromise = loadSoulForEdit(a.hermesProfile)
}

let soulLoadPromise: Promise<void> | null = null

async function loadSoulForEdit(profile: string | undefined) {
  const name = (profile || '').trim()
  if (!name) return
  try {
    const soul = await getHermesSoul(name)
    if (!form.soulMd.trim()) {
      form.soulMd = soul.content || ''
    }
  } catch {
    /* 智能体配置不存在时保持空，保存时需用户填写灵魂 */
  }
}

/** 确定：校验/保存失败时 reject，避免弹窗被误关（antd Modal @ok 约定） */
async function onModalOk() {
  await submitForm()
}

async function submitForm() {
  if (soulLoadPromise) {
    await soulLoadPromise
    soulLoadPromise = null
  }
  const code = form.code.trim()
  const name = form.name.trim()
  const soulMd = form.soulMd.trim()
  if (!code || !name) {
    message.warning('请填写编码与名称')
    throw new Error('__validation__')
  }
  if (!/^[\w-]+$/.test(code)) {
    message.warning('编码仅允许字母、数字、下划线与连字符')
    throw new Error('__validation__')
  }
  if (!soulMd) {
    message.warning('请填写灵魂 SOUL.md')
    throw new Error('__validation__')
  }
  const pr =
    form.priority == null || Number.isNaN(Number(form.priority))
      ? 100
      : Math.min(9999, Math.max(0, Number(form.priority)))
  const payload: UpsertAgentPayload = {
    code,
    name,
    category: form.category,
    description: form.description.trim(),
    icon: portraitIdForAgent(form.icon, code),
    modelCode: '',
    welcomeTitle: form.welcomeTitle.trim(),
    welcomeIntro: form.welcomeIntro.trim(),
    presetChat1: form.presetChat1.trim(),
    presetChat2: form.presetChat2.trim(),
    presetChat3: form.presetChat3.trim(),
    hermesProfile: form.hermesProfile.trim() || code,
    soulMd,
    priority: pr,
    enabled: form.enabled,
  }
  submitting.value = true
  try {
    const saved = await upsertRegistryAgent(payload)
    if (agentContext.activeAgent?.code === saved.code) {
      agentContext.setActiveAgent(saved)
    }
    message.success(editing.value ? '修改已保存' : '注册成功')
    modalOpen.value = false
    await loadAgents()
  } catch (e) {
    const msg = e instanceof Error ? e.message : ''
    if (msg !== '__validation__') {
      message.error(msg || '保存失败')
    }
    throw e
  } finally {
    submitting.value = false
  }
}

async function openOfficerPresetModal() {
  officerSubmitting.value = false
  try {
    const presets = await fetchWelcomePresets()
    officerForm.presetChat1 = presets.presetChat1 || ''
    officerForm.presetChat2 = presets.presetChat2 || ''
    officerForm.presetChat3 = presets.presetChat3 || ''
    officerForm.officerPortrait = resolveOfficerPortraitId(presets.officerPortrait)
  } catch {
    officerForm.presetChat1 = bootstrap.presetChat1 || ''
    officerForm.presetChat2 = bootstrap.presetChat2 || ''
    officerForm.presetChat3 = bootstrap.presetChat3 || ''
    officerForm.officerPortrait = resolveOfficerPortraitId(bootstrap.officerPortrait)
  }
  officerModalOpen.value = true
}

async function onOfficerModalOk() {
  officerSubmitting.value = true
  try {
    const saved = await updateWelcomePresets({
      presetChat1: officerForm.presetChat1.trim(),
      presetChat2: officerForm.presetChat2.trim(),
      presetChat3: officerForm.presetChat3.trim(),
      officerPortrait: officerForm.officerPortrait,
    })
    officerForm.presetChat1 = saved.presetChat1 || ''
    officerForm.presetChat2 = saved.presetChat2 || ''
    officerForm.presetChat3 = saved.presetChat3 || ''
    officerForm.officerPortrait = resolveOfficerPortraitId(saved.officerPortrait)
    await bootstrap.reload()
    message.success('预置对话与头像已保存')
    officerModalOpen.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
    throw e
  } finally {
    officerSubmitting.value = false
  }
}

function goOfficerChat() {
  chatStore.newConversation()
  agentContext.clearActiveAgent()
  useHermesProfileStore().useDefaultProfile()
  void router.push({ name: 'chat' })
}

function goChat(a: AgentRegistryItem) {
  chatStore.newConversation()
  agentContext.setActiveAgent(a)
  useHermesProfileStore().syncFromAgent(a.hermesProfile)
  void router.push({ name: 'chat', query: { agent: a.code } })
}

function confirmDeleteAgent(a: AgentRegistryItem) {
  const profile = a.hermesProfile?.trim()
  const extra = profile
    ? `同时删除对应的智能体配置「${displayNameForHermesProfile(profile, agents.value) || a.name}」。`
    : ''
  Modal.confirm({
    title: '删除专业智能体',
    content: `确定删除「${a.name}」（${a.code}）？${extra}该专业智能体下的全部会话也会一并删除，删除后无法恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteRegistryAgent(a.code)
        chatStore.dropSessionsOfAgent(a.code, a.hermesProfile)
        if (agentContext.activeAgent?.code === a.code) {
          agentContext.clearActiveAgent()
        }
        message.success('已删除')
        await loadAgents()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
        return Promise.reject(e)
      }
    },
  })
}

onMounted(async () => {
  await userProfile.ensureLoaded()
  void bootstrap.ensureLoaded()
  void loadAgents()
})
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';
@import '@/styles/mixins.scss';

.market-view {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 28px;
  overflow: auto;
  background: var(--bg-base);
}

.market-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;

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

.search-bar {
  max-width: 420px;
  margin-bottom: 20px;
  border-radius: 34px;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 18px;
}

.agent-card {
  @include market-card;
  @include market-disabled;
  padding: 20px 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 168px;

  .card-hero {
    display: flex;
    align-items: flex-start;
    gap: 14px;
  }

  .meta {
    flex: 1;
    min-width: 0;
  }

  .name-row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }

  .name {
    margin: 0;
    flex: 1;
    min-width: 0;
    font-size: var(--font-size-lg);
    font-weight: var(--font-weight-semibold);
    line-height: 1.35;
    color: var(--text-primary);
  }

  .status {
    flex-shrink: 0;
    margin-top: 2px;
    padding: 2px 10px;
    border-radius: 999px;
    font-size: var(--font-size-xs);
    line-height: 18px;
    letter-spacing: 0.01em;
    color: var(--text-secondary);
    background: rgba(118, 118, 128, 0.1);
    border: none;

    &.on {
      color: #1d7a46;
      background: rgba(52, 199, 89, 0.14);
      border: none;
    }
  }

  .desc {
    margin: 6px 0 0;
    font-size: var(--font-size-sm);
    line-height: var(--line-height-normal);
    color: var(--text-secondary);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .bind {
    margin: 8px 0 0;
    font-size: var(--font-size-xs);
    color: var(--text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .card-actions {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 8px;
    margin-top: auto;
    padding-top: 10px;
    border-top: 1px solid var(--border-subtle);

    .btn-delete {
      margin-right: auto;
    }
  }
}

.empty {
  margin: 48px 0;

  .empty-icon,
  .empty-glyph {
    width: 42px;
    height: 42px;
  }
}

.icon-picks {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 640px) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.icon-pick {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
  cursor: pointer;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;

  &:hover {
    border-color: var(--border-accent);
  }

  &.active {
    background: #fff;
    border-color: var(--color-primary);
    box-shadow: var(--shadow-sm);
    color: var(--color-primary-dark);
    font-weight: var(--font-weight-semibold);
  }
}

.agent-form .hint {
  display: block;
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.form-section-hint {
  margin: 0 0 12px;
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  color: var(--text-muted);
}

.enable-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.enable-hint {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
</style>
