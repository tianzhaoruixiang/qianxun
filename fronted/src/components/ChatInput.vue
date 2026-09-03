<template>
  <div class="chat-input">
    <!-- 选择器(左) + 会话操作(右) -->
    <div class="header-row">
      <div v-if="userProfile.isAdmin" class="header-selectors">
        <ModelSelector />
        <HermesProfileSelector />
        <div class="context-usage" :title="usageTitle">
          <span class="selector-label">上下文</span>
          <div class="toolbar-chip usage-status">
            <span class="usage-track" aria-hidden="true">
              <i :style="{ width: usagePercent + '%' }"></i>
            </span>
            <span class="usage-text">{{ chatStore.contextCompacting ? '压缩中' : (usageLabel || '—') }}</span>
          </div>
        </div>
        <Tooltip title="刷新模型与专业智能体">
          <button
            type="button"
            class="toolbar-refresh refresh-btn"
            :disabled="toolbarRefreshing"
            @click="onRefreshToolbar"
          >
            <AppGlyph name="reload" size="sm" />
          </button>
        </Tooltip>
      </div>
      <div class="session-actions">
        <button class="session-btn" @click="handleNewChat">
          <img :src="addChatIcon" alt="new chat" class="btn-icon" />
          <span>新建对话</span>
        </button>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="input-container">
      <div class="input-area">
        <div
          v-if="slashOpen"
          class="slash-menu"
          role="listbox"
          aria-label="斜杠命令"
        >
          <div class="slash-menu-head">{{ slashMenuTitle }}</div>
          <div v-if="slashItems.length" class="slash-menu-list">
            <button
              v-for="(item, idx) in slashItems"
              :key="item.id"
              type="button"
              class="slash-item"
              :class="{ active: idx === slashHighlight, current: item.kind === 'agent' && item.current }"
              role="option"
              :aria-selected="idx === slashHighlight"
              @mousedown.prevent="selectSlashItem(item)"
              @mouseenter="slashHighlight = idx"
            >
              <span class="slash-item-icon">
                <AppGlyph v-if="item.kind === 'command' && (item.id === 'goal' || item.id === 'goal-set' || item.id === 'goal-clear')" name="goal" size="sm" />
                <AppGlyph v-else-if="item.kind === 'command' && (item.id === 'plan' || item.id === 'plan-create' || item.id === 'plan-execute')" name="document" size="sm" />
                <AppGlyph v-else-if="item.kind === 'command' && (item.id === 'agents' || item.id === 'agents-status' || item.id === 'tasks')" name="agent" size="sm" />
                <AppGlyph v-else-if="item.kind === 'command' || item.kind === 'skill'" name="skill" size="sm" />
                <AgentAvatar
                  v-else
                  :group-key="item.agent ? `code:${item.agent.code}` : DIGITAL_OFFICER_KEY"
                  :label="item.label"
                  :icon="item.agent?.icon"
                  size="sm"
                />
              </span>
              <span class="slash-item-text">
                <span class="slash-item-label">{{ item.label }}</span>
                <span v-if="item.caption" class="slash-item-caption">{{ item.caption }}</span>
              </span>
            </button>
          </div>
          <div v-else class="slash-empty">{{ slashEmptyText }}</div>
        </div>

        <GoalComposer
          v-if="goalComposerOpen"
          :initial="chatStore.sessionGoal"
          :draft-title="goalDraftTitle"
          @confirm="onGoalConfirm"
          @cancel="closeGoalComposer"
        />

        <a-textarea
          v-show="!goalComposerOpen"
          v-model:value="chatStore.inputText"
          :placeholder="inputPlaceholder"
          :auto-size="{ minRows: 2, maxRows: 6 }"
          :disabled="chatStore.isLoading"
          class="message-textarea"
          @keydown="handleKeydown"
          @keyup="syncSlashFromEvent"
          @click="syncSlashFromEvent"
          @input="syncSlashFromEvent"
        />

        <div v-if="pendingSkill" class="pending-files">
          <span class="pending-chip skill-chip" :title="pendingSkillCaption">
            <AppGlyph name="skill" size="sm" />
            <span class="pending-name">{{ pendingSkillCaption }}</span>
            <button type="button" class="pending-remove" aria-label="取消技能" @click="pendingSkill = null">×</button>
          </span>
        </div>

        <div v-if="pendingFiles.length" class="pending-files">
          <span
            v-for="f in pendingFiles"
            :key="f.id"
            class="pending-chip"
          >
            <span class="pending-name" :title="f.name">{{ f.name }}</span>
            <button type="button" class="pending-remove" aria-label="移除附件" @click="removePending(f.id)">×</button>
          </span>
        </div>

        <input
          ref="fileInputRef"
          type="file"
          class="hidden-file"
          multiple
          :accept="fileAccept"
          @change="onFilePicked"
        />

        <!-- 快捷功能按钮组 -->
        <div class="quick-actions">
          <Tooltip title="附件">
            <button class="action-btn" @click="handleAction('attachment')">
              <AppGlyph name="clip" size="lg" />
            </button>
          </Tooltip>
          <Tooltip title="图片">
            <button class="action-btn" @click="handleAction('image')">
              <AppGlyph name="image" size="lg" />
            </button>
          </Tooltip>
          <Tooltip title="文档（Word / Excel）">
            <button class="action-btn" @click="handleAction('document')">
              <AppGlyph name="document" size="lg" />
            </button>
          </Tooltip>
          <!-- 流式输出中：停止；停止请求发出后禁用，避免连点 -->
          <Tooltip v-if="chatStore.isLoading || chatStore.isStopping" :title="chatStore.isStopping ? '正在停止' : '停止输出'">
            <button
              type="button"
              class="stop-stream-btn"
              :disabled="chatStore.isStopping"
              @click="handleStopStream"
            >
              <AppGlyph name="stop" size="sm" />
              <span class="stop-stream-label">{{ chatStore.isStopping ? '停止中' : '停止' }}</span>
            </button>
          </Tooltip>
          <Tooltip v-else title="发送">
            <button
              type="button"
              class="send-btn"
              :class="{ 'send-btn-disabled': isSendDisabled }"
              :disabled="isSendDisabled"
              @click="handleSend"
            >
              <img :src="sendIcon" alt="发送" />
            </button>
          </Tooltip>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Tooltip } from 'ant-design-vue'
import AppGlyph from '@/components/AppGlyph.vue'
import AgentAvatar from '@/components/AgentAvatar.vue'
import addChatIcon from '@/assets/images/add-chat.svg'
import sendIcon from '@/assets/images/send.png'
import { useChatStore } from '@/stores/useChatStore'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { useDataFilesStore } from '@/stores/useDataFilesStore'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { uploadDataFiles, type DataFileRow } from '@/api/files'
import { listHermesSkills, type HermesSkillItem } from '@/api/hermes'
import type { AgentRegistryItem } from '@/api/registry'
import { formatTokenCount, formatUsdCost } from '@/utils/contextUsage'
import { useResolvedContextWindow } from '@/composables/useResolvedContextWindow'
import ModelSelector from './ModelSelector.vue'
import HermesProfileSelector from './HermesProfileSelector.vue'
import { useKnowledgeStore } from '@/stores/useKnowledgeStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import { useUserProfileStore } from '@/stores/useUserProfileStore'
import { DIGITAL_OFFICER_KEY, displayModelLabel } from '@/utils/agentDisplay'
import { useSystemName } from '@/utils/systemName'
import { sanitizeUserFacingText } from '@/utils/userFacingCopy'
import { brandCopy } from '@/utils/brandCopy'
import {
  buildGoalItems,
  buildPlanItems,
  buildRootItemsWithSkills,
  buildAgentItems,
  buildSkillItems,
  detectSlashContext,
  parseSendSlash,
  replaceSlashToken,
  type SlashItem,
} from '@/utils/slashCommands'
import GoalComposer from './GoalComposer.vue'
import { formatGoalUserMessage, type SessionGoal } from '@/utils/sessionGoal'
import {
  DEFAULT_PLAN_CREATE_TASK,
  DEFAULT_PLAN_EXECUTE_TASK,
  LOCAL_PLAN_EXECUTE_DISPLAY,
  PLAN_EXECUTE_SKILL,
  PLAN_SKILL,
} from '@/utils/planCommands'

const chatStore = useChatStore()
const bootstrapStore = useBootstrapStore()
const dataFilesStore = useDataFilesStore()
const knowledgeStore = useKnowledgeStore()
const hermesProfileStore = useHermesProfileStore()
const userProfile = useUserProfileStore()
const agentContext = useAgentContextStore()
const systemName = useSystemName()
const { resolvedContextWindow } = useResolvedContextWindow()
const route = useRoute()
const router = useRouter()
const toolbarRefreshing = ref(false)

const fileInputRef = ref<HTMLInputElement | null>(null)
const fileAccept = ref('*/*')
const pendingFiles = ref<DataFileRow[]>([])
const uploading = ref(false)
const pendingSkill = ref<HermesSkillItem | null>(null)
const goalComposerOpen = ref(false)
const goalDraftTitle = ref('')
const slashCursor = ref(0)
const slashHighlight = ref(0)
const slashDismissed = ref(false)
const skills = ref<HermesSkillItem[]>([])
const skillsLoading = ref(false)
const skillsForProfile = ref('')

const ACCEPT: Record<string, string> = {
  attachment: '.doc,.docx,.xls,.xlsx,.csv,.pdf,.txt,.md,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.webp',
  image: 'image/png,image/jpeg,image/gif,image/webp,image/bmp,image/svg+xml',
  document: '.doc,.docx,.xls,.xlsx,.csv',
}

onMounted(() => {
  void bootstrapStore.ensureLoaded()
  void knowledgeStore.ensureLoaded()
  void agentContext.ensureAgents()
  if (!hermesProfileStore.selectedProfile.trim()) {
    hermesProfileStore.useDefaultProfile()
  }
  void hermesProfileStore.refresh(false)
})

async function onRefreshToolbar() {
  if (toolbarRefreshing.value) return
  toolbarRefreshing.value = true
  try {
    await Promise.all([
      agentContext.ensureAgents(true),
      hermesProfileStore.refresh(true),
      knowledgeStore.refreshRegistry(),
      bootstrapStore.reload(),
    ])
    knowledgeStore.syncSelectionForActiveAgent(agentContext.activeAgent)
    skillsForProfile.value = ''
    if (!hermesProfileStore.profiles.length) {
      message.warning('未获取到专业智能体，请稍后重试或刷新配置')
    } else {
      const model = bootstrapStore.claudeChatModel || displayModelLabel(hermesProfileStore.currentModelName())
      const win = hermesProfileStore.currentContextWindow()
      message.success(
        model
          ? `已刷新 ${hermesProfileStore.profiles.length} 个专业智能体，当前模型：${model}${win > 0 ? `，上下文 ${formatTokenCount(win)}` : ''}`
          : `已刷新 ${hermesProfileStore.profiles.length} 个专业智能体`,
      )
    }
  } catch (e) {
    message.error(e instanceof Error ? sanitizeUserFacingText(e.message) : '刷新失败')
  } finally {
    toolbarRefreshing.value = false
  }
}

const inputPlaceholder = computed(() => {
  const skill = pendingSkill.value
  if (skill?.name) {
    if (skill.name === PLAN_SKILL) {
      return '描述要规划的任务（可留空则按当前对话推断），回车生成计划'
    }
    return `描述要用技能「${skill.name}」做什么，回车发送`
  }
  return brandCopy.inputPlaceholder
})

const pendingSkillCaption = computed(() => {
  const skill = pendingSkill.value
  if (!skill) return ''
  if (skill.name === PLAN_SKILL) return '计划：生成实施计划'
  return `技能：${skill.name}`
})

function resolvePlanSkill(): HermesSkillItem {
  const hit = skills.value.find((s) => s.name === PLAN_SKILL && s.enabled)
  if (hit) return hit
  return {
    name: PLAN_SKILL,
    enabled: true,
    description: '生成实施计划并保存到工作区计划目录',
    category: 'software-development',
  }
}

const usagePercent = computed(() => {
  const u = chatStore.sessionUsage
  if (!u) return 0
  if (typeof u.contextPercent === 'number') {
    return Math.min(100, Math.max(0, u.contextPercent))
  }
  const used = u.contextUsed ?? 0
  const win = resolvedContextWindow.value
  if (win > 0) return Math.min(100, (used * 100) / win)
  return 0
})

const usageLabel = computed(() => {
  const u = chatStore.sessionUsage
  const used = u?.contextUsed ?? 0
  const win = resolvedContextWindow.value
  const prefix = u?.estimatedOccupancy ? '~' : ''
  if (win > 0) return `${prefix}${formatTokenCount(used)} / ${formatTokenCount(win)}`
  if (used) return `${prefix}占用 ${formatTokenCount(used)}`
  return ''
})

const usageTitle = computed(() => {
  const u = chatStore.sessionUsage
  const compacting = chatStore.contextCompacting
  const win = resolvedContextWindow.value
  const context = u?.contextUsed
  const billedIn = u?.promptTokens
  const treeTotal = (u?.treePromptTokens ?? 0) + (u?.treeCompletionTokens ?? 0)
  const contextHint = u?.estimatedOccupancy
    ? '会话正文粗估'
    : u?.contextSnapshot
      ? 'Claude SDK /context'
      : u?.sessionSnapshot
        ? 'Dashboard 会话快照'
        : '最近一次模型 prompt'
  const parts = [
    compacting ? '正在压缩上下文' : '',
    win > 0 ? `当前模型最大上下文 ${formatTokenCount(win)}` : '',
    context != null
      ? `当前占用 ${formatTokenCount(context)}（${contextHint}）`
      : (win > 0 ? '尚未产生占用' : ''),
    billedIn != null
      ? `累计输入 ${formatTokenCount(billedIn)}（主循环各轮之和${context != null && billedIn > context ? '，大于当前占用属正常' : ''}）`
      : '',
    u?.completionTokens != null ? `累计输出 ${formatTokenCount(u.completionTokens)}` : '',
    treeTotal > 0 ? `含子任务 ${formatTokenCount(treeTotal)}` : '',
    u?.totalCostUsd != null && u.totalCostUsd > 0 ? `估算成本 ${formatUsdCost(u.totalCostUsd)}` : '',
    usagePercent.value ? `已用窗口 ${usagePercent.value.toFixed(1)}%` : '',
  ].filter(Boolean)
  return parts.join(' · ')
})

/** 发送按钮是否禁用 */
const isSendDisabled = computed(() => {
  return uploading.value
    || (!chatStore.inputText.trim() && pendingFiles.value.length === 0 && !pendingSkill.value)
    || chatStore.isLoading
    || chatStore.isStopping
})

const slashCtx = computed(() => detectSlashContext(chatStore.inputText, slashCursor.value))
const slashOpen = computed(() => !!slashCtx.value && !slashDismissed.value && !goalComposerOpen.value)

const slashItems = computed((): SlashItem[] => {
  const ctx = slashCtx.value
  if (!ctx) return []
  if (ctx.mode === 'agents') {
    return buildAgentItems(
      agentContext.agents,
      ctx.query,
      agentContext.activeAgent?.code,
      { includeStatus: ctx.trigger !== '@' },
    )
  }
  if (ctx.mode === 'skill') {
    return buildSkillItems(skills.value, ctx.query)
  }
  if (ctx.mode === 'plan') {
    return buildPlanItems(ctx.query)
  }
  if (ctx.mode === 'goal') {
    return buildGoalItems(ctx.query, !!chatStore.sessionGoal)
  }
  return buildRootItemsWithSkills(
    agentContext.agents,
    ctx.query,
    agentContext.activeAgent?.code,
    !!chatStore.sessionGoal,
    skills.value,
  )
})

const slashMenuTitle = computed(() => {
  if (slashCtx.value?.trigger === '@') return '指定智能体运行'
  if (slashCtx.value?.mode === 'agents') return '智能体 / 子任务'
  if (slashCtx.value?.mode === 'skill') return '选择技能'
  if (slashCtx.value?.mode === 'plan') return '计划 / 执行'
  if (slashCtx.value?.mode === 'goal') return '长程目标'
  return '斜杠指令'
})

const slashEmptyText = computed(() => {
  if (slashCtx.value?.mode === 'agents') return '没有匹配的智能体或命令'
  if (slashCtx.value?.mode === 'skill') {
    if (skillsLoading.value) return '正在加载技能…'
    return '当前智能体没有已启用的技能'
  }
  if (slashCtx.value?.mode === 'plan') return '没有匹配的计划命令'
  if (slashCtx.value?.mode === 'goal') return '没有匹配的目标命令'
  return '没有匹配的指令'
})

watch(
  slashCtx,
  (now, prev) => {
    if (now && (!prev || now.start !== prev.start || now.mode !== prev.mode)) {
      slashDismissed.value = false
    }
  },
)

watch(slashItems, (now, prev) => {
  const same = now.length === prev?.length && now.every((item, i) => item.id === prev[i]?.id)
  if (!same) slashHighlight.value = 0
  else if (slashHighlight.value >= now.length) slashHighlight.value = 0
})

watch(
  () => [slashOpen.value, slashHighlight.value] as const,
  ([open]) => {
    if (!open) return
    void nextTick(() => {
      document.querySelector('.chat-input .slash-item.active')?.scrollIntoView({ block: 'nearest' })
    })
  },
)

watch(
  () => slashCtx.value?.mode,
  (mode) => {
    if (mode === 'skill' || mode === 'plan' || mode === 'root') void loadSkillsForMenu()
  },
)

watch(slashOpen, (open) => {
  if (open) {
    void agentContext.ensureAgents()
    void loadSkillsForMenu()
  }
})

watch(
  () => hermesProfileStore.selectedProfile,
  () => {
    skillsForProfile.value = ''
  },
)

async function loadSkillsForMenu() {
  const profile = (hermesProfileStore.selectedProfile || 'default').trim()
  if (skillsForProfile.value === profile) return
  skillsLoading.value = true
  try {
    skills.value = await listHermesSkills(profile)
    skillsForProfile.value = profile
  } catch {
    skills.value = []
  } finally {
    skillsLoading.value = false
  }
}

function nativeTextarea(e?: Event): HTMLTextAreaElement | null {
  const t = e?.target
  if (t instanceof HTMLTextAreaElement) return t
  return document.querySelector('.chat-input textarea')
}

function syncSlashFromEvent(e: Event) {
  const el = nativeTextarea(e)
  slashCursor.value = el?.selectionStart ?? chatStore.inputText.length
}

async function restoreCaret(pos: number) {
  await nextTick()
  const el = nativeTextarea()
  if (!el) return
  el.focus()
  const n = Math.max(0, Math.min(pos, el.value.length))
  el.setSelectionRange(n, n)
  slashCursor.value = n
}

async function applyAgent(agent: AgentRegistryItem | null, opts?: { preserveInput?: boolean }) {
  const keep = opts?.preserveInput === false ? '' : chatStore.inputText
  const same = agent
    ? agentContext.activeAgent?.code === agent.code
    : !agentContext.activeAgent
  await agentContext.ensureAgents()
  await knowledgeStore.ensureLoaded()
  pendingSkill.value = null
  skillsForProfile.value = ''
  const currentSessionId =
    (typeof route.params.sessionId === 'string' ? route.params.sessionId : '')
    || chatStore.conversationId
    || ''
  if (!agent) {
    agentContext.clearActiveAgent()
    hermesProfileStore.useDefaultProfile()
    knowledgeStore.syncSelectionForActiveAgent(null)
    if (!same) {
      chatStore.newConversation()
      chatStore.inputText = keep
    } else if (route.query.agent) {
      // 同智能体仅清 query 时保留 sessionId，避免 hydrate 清空当前会话
      await router.replace({ name: 'chat', params: { sessionId: currentSessionId } })
    }
    return
  }
  agentContext.setActiveAgent(agent)
  const profile = (agent.hermesProfile || '').trim()
  if (profile) hermesProfileStore.syncFromAgent(profile)
  else hermesProfileStore.useDefaultProfile()
  knowledgeStore.syncSelectionForActiveAgent(agent)
  if (!same) {
    chatStore.newConversation()
    chatStore.inputText = keep
  } else if (route.query.agent !== agent.code) {
    await router.replace({
      name: 'chat',
      params: { sessionId: currentSessionId },
      query: { agent: agent.code },
    })
  }
}

async function selectSlashItem(item: SlashItem) {
  const ctx = slashCtx.value
  if (!ctx) return
  if (item.kind === 'command') {
    if (item.id === 'agents') {
      chatStore.inputText = replaceSlashToken(chatStore.inputText, ctx, '/agents ')
      await restoreCaret(ctx.start + '/agents '.length)
      void agentContext.ensureAgents()
      return
    }
    if (item.id === 'tasks' || item.id === 'agents-status') {
      chatStore.inputText = ''
      slashDismissed.value = true
      pendingSkill.value = null
      await chatStore.sendMessage('【子智能体】查看运行中的任务', undefined, { agentsStatus: true })
      return
    }
    if (item.id === 'mcp') {
      chatStore.inputText = ''
      slashDismissed.value = true
      await router.push({ name: 'mcp-market' })
      return
    }
    if (item.id === 'plugin') {
      chatStore.inputText = ''
      slashDismissed.value = true
      await router.push({ name: 'plugin-market' })
      return
    }
    if (item.id === 'compact') {
      chatStore.inputText = ''
      slashDismissed.value = true
      pendingSkill.value = null
      await chatStore.sendMessage('/compact', undefined, { slashCommand: '/compact' })
      return
    }
    if (item.id === 'skill') {
      chatStore.inputText = replaceSlashToken(chatStore.inputText, ctx, '/skill ')
      await restoreCaret(ctx.start + '/skill '.length)
      void loadSkillsForMenu()
      return
    }
    if (item.id === 'plan') {
      chatStore.inputText = replaceSlashToken(chatStore.inputText, ctx, '/plan ')
      await restoreCaret(ctx.start + '/plan '.length)
      void loadSkillsForMenu()
      return
    }
    if (item.id === 'plan-create') {
      const draft = (ctx.mode === 'plan' ? ctx.query : replaceSlashToken(chatStore.inputText, ctx, '').trim()).trim()
      slashDismissed.value = true
      await loadSkillsForMenu()
      pendingSkill.value = resolvePlanSkill()
      chatStore.inputText = draft
      await restoreCaret(chatStore.inputText.length)
      nativeTextarea()?.focus()
      return
    }
    if (item.id === 'plan-execute') {
      slashDismissed.value = true
      pendingSkill.value = null
      chatStore.inputText = ''
      const attachments = pendingFiles.value.map((f) => ({ id: f.id, name: f.name }))
      pendingFiles.value = []
      await chatStore.sendMessage(LOCAL_PLAN_EXECUTE_DISPLAY, attachments, {
        skillName: PLAN_EXECUTE_SKILL,
      })
      return
    }
    if (item.id === 'goal' || item.id === 'goal-set') {
      const leftover = replaceSlashToken(chatStore.inputText, ctx, '').trim()
      chatStore.inputText = ''
      openGoalComposer((ctx.mode === 'goal' ? ctx.query : leftover).trim())
      return
    }
    if (item.id === 'goal-clear') {
      chatStore.inputText = replaceSlashToken(chatStore.inputText, ctx, '')
      await chatStore.clearSessionGoal()
      message.success('已清除长程目标')
      await restoreCaret(ctx.start)
      return
    }
    if (item.label.startsWith('/')) {
      const ins = item.label.endsWith(' ') ? item.label : `${item.label} `
      chatStore.inputText = replaceSlashToken(chatStore.inputText, ctx, ins)
      slashDismissed.value = true
      await restoreCaret(ctx.start + ins.length)
      nativeTextarea()?.focus()
      return
    }
  }
  if (item.kind === 'agent') {
    if (ctx.trigger === '@') {
      // 保留 @智能体 作为发送指令，用户可在后面继续输入任务；发送时再切换并运行。
      const mention = `@${item.label} `
      chatStore.inputText = replaceSlashToken(chatStore.inputText, ctx, mention)
      slashDismissed.value = true
      await restoreCaret(ctx.start + mention.length)
      nativeTextarea()?.focus()
      return
    }
    chatStore.inputText = replaceSlashToken(chatStore.inputText, ctx, '')
    await applyAgent(item.agent)
    message.success(item.agent ? `已切换到「${item.label}」` : `已切换到「${systemName.value}」`)
    await restoreCaret(chatStore.inputText.length)
    return
  }
  if (item.kind === 'skill') {
    // 选中技能后挂到输入区，继续写任务描述再发送（不立刻下发）
    const leftover = replaceSlashToken(chatStore.inputText, ctx, '').trim()
    slashDismissed.value = true
    pendingSkill.value = item.skill
    chatStore.inputText = leftover
    await restoreCaret(chatStore.inputText.length)
    nativeTextarea()?.focus()
    return
  }
}

/**
 * 发送消息
 */
async function handleSend() {
  if (goalComposerOpen.value) return
  if (isSendDisabled.value) return
  if (slashOpen.value && slashItems.value.length) {
    await selectSlashItem(slashItems.value[slashHighlight.value])
    return
  }
  await agentContext.ensureAgents()
  const profile = (hermesProfileStore.selectedProfile || 'default').trim()
  if (skillsForProfile.value !== profile) {
    try {
      skills.value = await listHermesSkills(profile)
      skillsForProfile.value = profile
    } catch {
      /* 仍按输入解析 */
    }
  }
  const parsed = parseSendSlash({
    text: chatStore.inputText,
    skills: skills.value,
    agents: agentContext.agents,
    pendingSkillName: pendingSkill.value?.name,
  })
  if (parsed.goalAction === 'clear') {
    await chatStore.clearSessionGoal()
    message.success('已清除长程目标')
    chatStore.inputText = parsed.content
    if (!parsed.content && pendingFiles.value.length === 0) {
      pendingSkill.value = null
      return
    }
  }
  if (parsed.goalAction === 'open') {
    openGoalComposer(parsed.content)
    chatStore.inputText = ''
    return
  }
  if (parsed.agentsStatus) {
    const attachments = pendingFiles.value.map((f) => ({ id: f.id, name: f.name }))
    pendingFiles.value = []
    pendingSkill.value = null
    await chatStore.sendMessage(
      parsed.content || '【子智能体】查看运行中的任务',
      attachments,
      { agentsStatus: true },
    )
    return
  }
  if (parsed.agent !== undefined) {
    await applyAgent(parsed.agent, { preserveInput: false })
  }
  let content = parsed.content
  if (!content && pendingFiles.value.length === 0) {
    if (parsed.skillName === PLAN_SKILL) content = DEFAULT_PLAN_CREATE_TASK
    else if (parsed.skillName === PLAN_EXECUTE_SKILL) content = DEFAULT_PLAN_EXECUTE_TASK
    else if (parsed.skillName) content = '请按该技能执行。'
    else return
  }
  if (parsed.skillName === PLAN_EXECUTE_SKILL && (!content || content === LOCAL_PLAN_EXECUTE_DISPLAY)) {
    content = DEFAULT_PLAN_EXECUTE_TASK
  }
  const attachments = pendingFiles.value.map((f) => ({ id: f.id, name: f.name }))
  pendingFiles.value = []
  pendingSkill.value = null
  await chatStore.sendMessage(content, attachments, {
    skillName: parsed.skillName,
    slashCommand: parsed.slashCommand,
  })
}

function openGoalComposer(draft: string) {
  goalDraftTitle.value = draft.trim()
  goalComposerOpen.value = true
  slashDismissed.value = true
}

function closeGoalComposer() {
  goalComposerOpen.value = false
  goalDraftTitle.value = ''
  void nextTick(() => nativeTextarea()?.focus())
}

async function onGoalConfirm(goal: SessionGoal) {
  goalComposerOpen.value = false
  const attachments = pendingFiles.value.map((f) => ({ id: f.id, name: f.name }))
  pendingFiles.value = []
  pendingSkill.value = null
  await chatStore.sendMessage(formatGoalUserMessage(goal), attachments, { goal })
}

function handleStopStream() {
  if (chatStore.isStopping) return
  void chatStore.stopStreaming()
}

/**
 * 新建对话
 */
function handleNewChat() {
  pendingFiles.value = []
  pendingSkill.value = null
  closeGoalComposer()
  chatStore.newConversation()
}

/**
 * 键盘事件处理 - Enter 发送 / Shift+Enter 换行
 */
function handleKeydown(e: KeyboardEvent) {
  if (goalComposerOpen.value) return
  syncSlashFromEvent(e)
  if (slashOpen.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      const n = slashItems.value.length
      if (!n) return
      slashHighlight.value = (slashHighlight.value + 1) % n
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      const n = slashItems.value.length
      if (!n) return
      slashHighlight.value = (slashHighlight.value - 1 + n) % n
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      slashDismissed.value = true
      return
    }
    if (e.key === 'Tab' || (e.key === 'Enter' && !e.shiftKey)) {
      if (slashItems.value.length) {
        e.preventDefault()
        void selectSlashItem(slashItems.value[slashHighlight.value])
        return
      }
    }
  }
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void handleSend()
  }
}

/**
 * 快捷功能：选择本地文件并作为本轮聊天附件上传（不使用「我的网盘」当前浏览目录）。
 */
function handleAction(type: string) {
  fileAccept.value = ACCEPT[type] || ACCEPT.attachment
  const el = fileInputRef.value
  if (!el) return
  el.value = ''
  el.click()
}

function removePending(id: string) {
  pendingFiles.value = pendingFiles.value.filter((f) => f.id !== id)
}

/** 聊天框上传专用目录：仅这些会作为本轮 fileIds 进入智能体上下文，不会从网盘其它文件抽取正文 */
const CHAT_UPLOAD_FOLDER = '聊天附件'

async function onFilePicked(ev: Event) {
  const input = ev.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length) return
  uploading.value = true
  try {
    const result = await uploadDataFiles(files, CHAT_UPLOAD_FOLDER)
    for (const row of result.files || []) {
      if (!pendingFiles.value.some((p) => p.id === row.id)) {
        pendingFiles.value.push(row)
      }
    }
    for (const err of result.errors || []) {
      message.error(err)
    }
    await dataFilesStore.loadFiles()
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';
@import '@/styles/mixins.scss';

.chat-input {
  flex-shrink: 0;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  padding: 8px 24px 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.42) 0%, rgba(255, 255, 255, 0.62) 100%);
  border-top: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
  position: relative;
  z-index: 2;

  // 顶部行：选择器(左) + 会话按钮(右)
  .header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    width: 100%;
    margin-bottom: 8px;

    .header-selectors {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      min-width: 0;
    }

    .context-usage {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 0;
    }

    .selector-label {
      font-size: var(--font-size-xs);
      color: var(--text-muted, #94a3b8);
      flex-shrink: 0;
      line-height: 1.25;
      display: inline-flex;
      align-items: center;
      height: 32px;
    }

    .toolbar-chip.usage-status {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 160px;
      max-width: min(280px, 36vw);
      min-height: 32px;
      height: 32px;
      padding: 0 11px;
      border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.2));
      border-radius: 6px;
      background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58));
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
      box-sizing: border-box;
    }

    .usage-track {
      flex: 1;
      height: 6px;
      min-width: 48px;
      border-radius: 999px;
      background: rgba(56, 102, 245, 0.12);
      overflow: hidden;

      i {
        display: block;
        height: 100%;
        background: linear-gradient(90deg, var(--color-primary, #3b82f6), #36a9ff);
        border-radius: inherit;
      }
    }

    .usage-text {
      flex-shrink: 0;
      font-size: var(--font-size-sm);
      color: var(--text-secondary, #475569);
      white-space: nowrap;
    }

    .toolbar-refresh {
      @include icon-btn(var(--icon-btn-size));
      border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.2));
      border-radius: 6px;
      background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58));
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
      cursor: pointer;
      color: var(--text-muted, #94a3b8);
      transition: border-color 0.2s ease;

      :deep(.ag-icon) {
        opacity: 0.72;
      }

      &:hover:not(:disabled) {
        border-color: var(--color-primary-light);

        :deep(.ag-icon) {
          opacity: 0.9;
        }
      }

      &:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
    }

    .session-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-left: auto;
      flex-shrink: 0;

      .session-btn {
        @include icon-text-btn(6px);
        height: var(--icon-btn-size);
        padding: 0 12px;
        border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.2));
        background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58));
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55), 0 4px 6px 0 rgba(15, 59, 110, 0.06);
        border-radius: 6px;
        cursor: pointer;
        font-size: var(--font-size-sm);
        color: var(--text-primary);
        transition: all 0.2s ease;
        white-space: nowrap;

        &:hover {
          border-color: var(--color-primary-light);

          .btn-icon {
            color: var(--color-primary);
          }
        }

        .btn-icon {
          width: var(--icon-size-sm);
          height: var(--icon-size-sm);
          object-fit: contain;
        }
      }
    }
  }

  // 输入区主体
  .input-container {
    display: flex;
    align-items: flex-end;
    gap: 12px;

    .input-area {
      flex: 1;
      min-width: 0;
      position: relative;
      border-radius: var(--radius-md);
      padding: 12px 16px;
      transition: border-color 0.28s ease, box-shadow 0.28s cubic-bezier(0.22, 1, 0.36, 1);
      display: flex;
      flex-direction: column;
      gap: 4px;
      background: var(--card-bg-solid, rgba(255, 255, 255, 0.92));
      backdrop-filter: blur(20px) saturate(1.35);
      -webkit-backdrop-filter: blur(20px) saturate(1.35);
      border: 1px solid var(--card-border, rgba(0, 0, 0, 0.04));
      box-shadow: var(--card-shadow);

      &:focus-within {
        border-color: var(--card-border-hover, rgba(0, 0, 0, 0.1));
        box-shadow: var(--card-shadow-hover);
      }

      .message-textarea {
        border: none !important;
        box-shadow: none !important;
        background: transparent !important;
        padding: 0 !important;
        resize: none !important;
        min-height: calc(2 * var(--font-size-md) * var(--line-height-relaxed)) !important;
        font-size: var(--font-size-md);
        line-height: var(--line-height-relaxed);
        color: var(--text-primary);
        font-family: var(--font-family-base);

        &::placeholder {
          color: var(--text-muted);
        }

        &:focus {
          box-shadow: none !important;
        }
      }

      .slash-menu {
        position: absolute;
        left: 0;
        right: 0;
        bottom: calc(100% + 8px);
        z-index: 20;
        max-height: min(320px, 46vh);
        overflow: hidden;
        display: flex;
        flex-direction: column;
        border-radius: 8px;
        border: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
        background: var(--chat-glass-strong, rgba(255, 255, 255, 0.92));
        backdrop-filter: blur(18px) saturate(1.12);
        -webkit-backdrop-filter: blur(18px) saturate(1.12);
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65), 0 12px 28px rgba(15, 59, 110, 0.12);
      }

      .slash-menu-head {
        @include text-caption;
        padding: 8px 12px 4px;
        color: var(--text-muted, #94a3b8);
      }

      .slash-menu-list {
        overflow-y: auto;
        padding: 4px 6px 8px;
        @include hide-scrollbar;
      }

      .slash-item {
        width: 100%;
        min-height: 32px;
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 4px 8px;
        border: none;
        border-radius: 6px;
        background: transparent;
        cursor: pointer;
        text-align: left;
        color: var(--text-primary);

        &.active,
        &:hover {
          background: rgba(56, 102, 245, 0.1);
        }

        &.current .slash-item-caption {
          color: var(--color-primary, #3b82f6);
        }
      }

      .slash-item-icon {
        flex-shrink: 0;
        width: var(--icon-btn-size-sm);
        height: var(--icon-btn-size-sm);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        line-height: 0;
      }

      .slash-item-text {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 0;
        justify-content: center;
      }

      .slash-item-label {
        font-size: var(--font-size-md);
        line-height: 1.25;
        @include text-ellipsis;
      }

      .slash-item-caption {
        @include text-caption;
        @include text-ellipsis;
      }

      .slash-empty {
        @include text-caption;
        padding: 8px 12px 12px;
      }

      .skill-chip {
        gap: 6px;
      }

      .pending-files {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        padding-top: 4px;
      }

      .pending-chip {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        max-width: 220px;
        padding: 2px 6px 2px 8px;
        border-radius: 999px;
        background: rgba(56, 102, 245, 0.08);
        font-size: var(--font-size-xs);
        color: var(--text-primary);
      }

      .pending-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .pending-remove {
        @include icon-btn(18px);
        border: none;
        background: transparent;
        cursor: pointer;
        color: var(--text-muted);
        font-size: var(--font-size-md);
        border-radius: 999px;

        &:hover {
          color: var(--text-primary);
          background: rgba(15, 23, 42, 0.06);
        }
      }

      .hidden-file {
        display: none;
      }

      // 快捷功能按钮组
      .quick-actions {
        display: flex;
        align-items: center;
        gap: 4px;
        padding-top: 4px;

        .action-btn {
          @include icon-btn(var(--icon-btn-size));
          border: none;
          background: transparent;
          border-radius: 6px;
          cursor: pointer;
          color: var(--text-muted);
          transition: all 0.2s ease;

          &:hover {
            background: var(--bg-base);
            color: var(--color-primary);
          }
        }
      }
    }

    // 发送按钮
    .send-btn {
      @include icon-btn(36px);
      width: 44px;
      border-radius: 8px;
      border: none;
      background: linear-gradient(55deg, #3E72D8 -15%, #36A6FE 94%);
      color: white;
      cursor: pointer;
      margin-left: auto;
      flex-shrink: 0;
      transition: all 0.2s ease;
      align-self: flex-end;

      &:hover:not(.send-btn-disabled) {
        transform: scale(1.05);
        filter: brightness(1.1);
      }

      &:active:not(.send-btn-disabled) {
        transform: scale(0.95);
      }

      &-disabled {
        background: #d1d5db;
        cursor: not-allowed;
      }

      img {
        width: var(--icon-size-sm);
        height: var(--icon-size-sm);
        display: block;
        object-fit: contain;
      }
    }

    .stop-stream-btn {
      @include icon-text-btn(6px);
      margin-left: auto;
      flex-shrink: 0;
      height: 36px;
      padding: 0 12px;
      border-radius: 8px;
      border: 1px solid rgba(220, 38, 38, 0.35);
      background: linear-gradient(180deg, #fff5f5 0%, #fee2e2 100%);
      color: #b91c1c;
      cursor: pointer;
      font-size: var(--font-size-sm);
      font-weight: var(--font-weight-semibold);
      transition: all 0.2s ease;

      &:hover:not(:disabled) {
        border-color: rgba(185, 28, 28, 0.55);
        background: linear-gradient(180deg, #fff1f2 0%, #fecaca 100%);
        color: #991b1b;
      }

      &:active:not(:disabled) {
        transform: scale(0.97);
      }

      &:disabled {
        cursor: not-allowed;
        opacity: 0.65;
      }

      .stop-stream-label {
        line-height: 1;
      }
    }
  }
}
</style>
