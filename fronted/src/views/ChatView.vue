<template>
  <div class="chat-view">
    <!-- 头部区域 -->
    <ChatHeader />
    <SessionGoalBanner
      :goal="chatStore.sessionGoal"
      :progress-text="goalProgressText"
      @clear="onClearGoal"
    />

    <!-- 主内容区：消息列表 / 欢迎语 + 提问目录 -->
    <div class="chat-stage">
      <div class="chat-body" ref="chatBodyRef">
        <!-- 空态：欢迎语 + 推荐问题 -->
        <WelcomeSection v-if="!chatStore.hasMessages" />

        <!-- 对话态：消息气泡列表；统计紧跟最后一条消息 -->
        <div v-else class="message-list" ref="messageListRef">
          <ChatBubble
            v-for="(msg, idx) in chatStore.messages"
            :key="msg.id"
            :message="msg"
            :highlighted="highlightedQuestionId === msg.id"
            :active-tool-key="activeToolMessageId === msg.id ? activeToolKey : null"
            :stream-active="
              chatStore.isLoading
              && msg.role === 'assistant'
              && idx === chatStore.messages.length - 1
            "
            @open-tool="onOpenTool"
          />
          <div class="session-stats" aria-live="polite">
            <span>本次会话</span>
            <span>用时 {{ chatStore.sessionStats.durationLabel }}</span>
            <span v-if="userProfile.isAdmin" :title="contextStatTitle">
              上下文 {{ contextStatLabel }}
            </span>
            <span :title="chatStore.sessionStats.outputTitle">输出 {{ chatStore.sessionStats.outputLabel }}</span>
            <span :title="chatStore.sessionStats.totalTitle">累计调用 {{ chatStore.sessionStats.totalLabel }}</span>
          </div>
        </div>
      </div>

      <ChatQuestionNav
        v-if="chatStore.hasMessages"
        :items="questionNavItems"
        :active-id="activeQuestionId"
        @jump="onJumpToQuestion"
      />
    </div>

    <!-- 下一步建议固定在输入框上方，不与消息抢滚动区 -->
    <div v-if="visibleSuggestions.length" class="chat-dock">
      <div class="next-steps">
        <div class="next-steps-label">下一步建议</div>
        <div class="next-steps-list">
          <button
            v-for="(item, sidx) in visibleSuggestions"
            :key="`${item}-${sidx}`"
            type="button"
            class="next-step-chip"
            @click="onSelectSuggestion(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <ChatInput ref="chatInputRef" />

    <ToolExecutionDrawer v-model:open="toolDrawerOpen" :tool="selectedTool" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import ChatHeader from '@/components/ChatHeader.vue'
import SessionGoalBanner from '@/components/SessionGoalBanner.vue'
import WelcomeSection from '@/components/WelcomeSection.vue'
import ChatBubble from '@/components/ChatBubble.vue'
import ChatQuestionNav from '@/components/ChatQuestionNav.vue'
import ChatInput from '@/components/ChatInput.vue'
import ToolExecutionDrawer from '@/components/ToolExecutionDrawer.vue'
import type { ToolCallInfo } from '@/types/chat'
import { fallbackNextStepSuggestions } from '@/utils/nextStepSuggestions'
import { useChatStore } from '@/stores/useChatStore'
import { useLayoutStore } from '@/stores/useLayoutStore'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useKnowledgeStore } from '@/stores/useKnowledgeStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { useUserProfileStore } from '@/stores/useUserProfileStore'
import { listRegistryAgents } from '@/api/registry'
import { useShortcuts } from '@/composables/useShortcuts'
import { consumePendingChatStarter } from '@/utils/pendingChatStarter'
import { formatTokenCount } from '@/utils/contextUsage'
import { useResolvedContextWindow } from '@/composables/useResolvedContextWindow'
import { message } from 'ant-design-vue'

const route = useRoute()
const chatStore = useChatStore()
const layoutStore = useLayoutStore()
const agentContext = useAgentContextStore()
const knowledgeStore = useKnowledgeStore()
const hermesProfileStore = useHermesProfileStore()
const bootstrap = useBootstrapStore()
const userProfile = useUserProfileStore()
const { resolvedContextWindow } = useResolvedContextWindow()

const contextMaxWindow = computed(() => resolvedContextWindow.value)

const contextStatLabel = computed(() => {
  const used = chatStore.sessionStats.contextUsed
  const win = contextMaxWindow.value
  const estimated = chatStore.sessionUsage?.estimatedOccupancy
  const usedLabel = used > 0 ? `${estimated ? '~' : ''}${formatTokenCount(used)}` : '—'
  if (win > 0) return `${usedLabel} / ${formatTokenCount(win)}`
  return usedLabel
})

const contextStatTitle = computed(() => {
  const win = contextMaxWindow.value
  const maxPart = win > 0 ? `当前模型最大上下文 ${formatTokenCount(win)}` : ''
  const usagePart = chatStore.sessionStats.contextTitle
  return [maxPart, usagePart].filter(Boolean).join(' · ')
})

const toolDrawerOpen = ref(false)
const activeToolMessageId = ref<string | null>(null)
const activeToolKey = ref<string | null>(null)
const activeToolCallId = ref<string | null>(null)
const openedToolSnapshot = ref<ToolCallInfo | null>(null)

function findLiveTool(messageId: string | null, toolKey: string | null, toolCallId: string | null): ToolCallInfo | null {
  if (!toolKey && !toolCallId) return null
  const msgs = chatStore.messages
  const preferred = messageId ? msgs.filter((m) => m.id === messageId) : []
  const search = preferred.length ? [...preferred, ...msgs.filter((m) => m.id !== messageId)] : msgs
  for (const msg of search) {
    const tools = msg.toolCalls
    if (!tools?.length) continue
    if (toolCallId) {
      const byId = tools.find((t) => t.toolCallId === toolCallId)
      if (byId) return byId
    }
    const sorted = [...tools].sort((a, b) => {
      const ao = a.contentOffset ?? Number.MAX_SAFE_INTEGER
      const bo = b.contentOffset ?? Number.MAX_SAFE_INTEGER
      if (ao !== bo) return ao - bo
      return (a.startedAt ?? 0) - (b.startedAt ?? 0)
    })
    const idxMatch = /^tool-(\d+)$/.exec(toolKey || '')
    if (idxMatch) {
      const i = Number(idxMatch[1])
      if (Number.isFinite(i) && sorted[i]) return sorted[i]
    }
    const byKey = sorted.find((t) => (t.toolCallId || '') === toolKey)
    if (byKey) return byKey
  }
  return null
}

/** 从会话消息中解析当前选中工具（流式更新时保持最新） */
const selectedTool = computed<ToolCallInfo | null>(() => {
  if (!toolDrawerOpen.value) return null
  return findLiveTool(activeToolMessageId.value, activeToolKey.value, activeToolCallId.value)
    ?? openedToolSnapshot.value
})

function onOpenTool(payload: { messageId: string; toolKey: string; tool: ToolCallInfo }) {
  const same =
    toolDrawerOpen.value
    && activeToolMessageId.value === payload.messageId
    && activeToolKey.value === payload.toolKey
  if (same) {
    toolDrawerOpen.value = false
    activeToolMessageId.value = null
    activeToolKey.value = null
    activeToolCallId.value = null
    openedToolSnapshot.value = null
    return
  }
  activeToolMessageId.value = payload.messageId
  activeToolKey.value = payload.toolKey
  activeToolCallId.value = payload.tool.toolCallId || null
  openedToolSnapshot.value = payload.tool
  toolDrawerOpen.value = true
}

watch(toolDrawerOpen, (open) => {
  if (!open) {
    activeToolMessageId.value = null
    activeToolKey.value = null
    activeToolCallId.value = null
    openedToolSnapshot.value = null
  }
})

watch(
  () => chatStore.conversationId,
  () => {
    toolDrawerOpen.value = false
    activeToolMessageId.value = null
    activeToolKey.value = null
    activeToolCallId.value = null
    openedToolSnapshot.value = null
    activeQuestionId.value = null
    highlightedQuestionId.value = null
    if (highlightTimer) {
      window.clearTimeout(highlightTimer)
      highlightTimer = 0
    }
  },
)

const goalProgressText = computed(() => {
  if (!chatStore.sessionGoal) return ''
  const last = [...chatStore.messages].reverse().find((m) => m.role === 'assistant' && m.toolCalls?.length)
  const tools = last?.toolCalls || []
  const relevant = tools.filter((t) => {
    const name = `${t.toolName || ''} ${t.displayName || ''} ${t.iconKind || ''}`.toLowerCase()
    return name.includes('todo') || name.includes('kanban') || name.includes('任务') || name.includes('看板')
  })
  if (!relevant.length) return ''
  const latest = relevant[relevant.length - 1]
  const label = latest.displayName || latest.toolName || '任务清单'
  const status = latest.status === 'completed' ? '已完成' : latest.status === 'error' ? '出错' : '进行中'
  return `近期步骤：${label} · ${status}`
})

async function onClearGoal() {
  await chatStore.clearSessionGoal()
  message.success('已清除长程目标')
}

async function syncAgentFromRoute() {
  if (route.name !== 'chat') return
  const code = typeof route.query.agent === 'string' ? route.query.agent : ''
  if (!code) {
    // 侧栏历史已 switchSession、或本智能体内新建对话时，不要冲掉当前专业智能体
    if (chatStore.conversationId || agentContext.activeAgent) return
    agentContext.clearActiveAgent()
    hermesProfileStore.useDefaultProfile()
    await knowledgeStore.ensureLoaded()
    knowledgeStore.syncSelectionForActiveAgent(null)
    void hermesProfileStore.refresh(false)
    return
  }
  try {
    const list = await listRegistryAgents(false)
    agentContext.replaceAgents(list)
    const found = list.find((x) => x.code === code)
    if (found) {
      agentContext.setActiveAgent(found)
      await knowledgeStore.ensureLoaded()
      knowledgeStore.syncSelectionForActiveAgent(found)
      hermesProfileStore.syncFromAgent(found.hermesProfile)
      // force+已加载时走静默刷新，补齐 profile.contextWindow，不闪「加载中」
      void hermesProfileStore.refresh(true)
      const pending = consumePendingChatStarter(found.code)
      if (pending) {
        await nextTick()
        void chatStore.sendMessage(pending)
      }
    } else {
      agentContext.clearActiveAgent()
      await knowledgeStore.ensureLoaded()
      knowledgeStore.syncSelectionForActiveAgent(null)
    }
  } catch {
    /* 保持已有 store 或空白 */
  }
}

watch(
  () => [route.name, route.query.agent] as const,
  () => {
    void syncAgentFromRoute()
  },
  { immediate: true },
)

watch(
  () => [route.name, route.params.sessionId] as const,
  ([name, sessionId]) => {
    if (name !== 'chat') return
    const id = typeof sessionId === 'string' ? sessionId : Array.isArray(sessionId) ? sessionId[0] : ''
    void chatStore.hydrateFromRoute(id || '')
  },
  { immediate: true },
)

function onSelectSuggestion(text: string) {
  void chatStore.sendMessage(text)
}

/** 仅最后一条已完成的助手消息：固定在输入框上方展示 */
const visibleSuggestions = computed(() => {
  if (chatStore.isLoading || !chatStore.hasMessages) return []
  const last = chatStore.messages[chatStore.messages.length - 1]
  if (!last || last.role !== 'assistant') return []
  const fromApi = (last.suggestions || []).map((x) => x.trim()).filter(Boolean)
  if (fromApi.length) return fromApi.slice(0, 3)
  if (!last.content?.trim()) return []
  const recent = chatStore.messages.slice(-8).map((m) => ({
    role: m.role,
    content: m.content,
  }))
  return fallbackNextStepSuggestions(last.content, recent)
})

/** 聊天内容区 DOM 引用 */
const chatBodyRef = ref<HTMLDivElement | null>(null)
const messageListRef = ref<HTMLDivElement | null>(null)
const activeQuestionId = ref<string | null>(null)
const highlightedQuestionId = ref<string | null>(null)
let highlightTimer = 0

const questionNavItems = computed(() =>
  chatStore.messages
    .filter((m) => m.role === 'user')
    .map((m) => {
      const full = (m.content || '').trim() || (m.attachments?.map((a) => a.name).join('、') ?? '')
      return {
        id: m.id,
        full: full || '（空提问）',
      }
    }),
)

function onJumpToQuestion(id: string) {
  activeQuestionId.value = id
  highlightedQuestionId.value = id
  stickToBottom.value = false
  if (highlightTimer) window.clearTimeout(highlightTimer)
  highlightTimer = window.setTimeout(() => {
    if (highlightedQuestionId.value === id) highlightedQuestionId.value = null
    highlightTimer = 0
  }, 2800)
  void nextTick(() => {
    const root = chatBodyRef.value
    const el = root?.querySelector(`[data-message-id="${CSS.escape(id)}"]`) as HTMLElement | null
    if (!root || !el) return
    ignoreScrollUntil = performance.now() + 400
    const elRect = el.getBoundingClientRect()
    const rootRect = root.getBoundingClientRect()
    const top = root.scrollTop + (elRect.top - rootRect.top) - 12
    root.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
  })
}

/** 输入框组件引用（用于聚焦） */
const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)

/**
 * 聚焦输入框
 */
function focusInput() {
  const el = (chatInputRef.value?.$el as HTMLElement)?.querySelector('textarea')
  if (el) {
    el.focus()
  }
}

// 注册全局快捷键
useShortcuts([
  {
    key: 'k',
    ctrl: true,
    handler: focusInput,
    description: '聚焦输入框',
  },
  {
    key: 'k',
    ctrl: true,
    shift: true,
    handler: () => chatStore.newConversation(),
    description: '新建对话',
  },
  {
    key: 'Escape',
    handler: () => { if (!layoutStore.rightPanelCollapsed) layoutStore.toggleRightPanel() },
    description: '关闭右侧面板',
  },
  {
    key: 'b',
    ctrl: true,
    handler: () => layoutStore.toggleSidebar(),
    description: '切换侧边栏',
  },
  {
    key: ']',
    ctrl: true,
    handler: () => layoutStore.toggleRightPanel(),
    description: '切换右侧面板',
  },
])

const NEAR_BOTTOM_PX = 120
const stickToBottom = ref(true)
let scrollRaf = 0
let ignoreScrollUntil = 0
let listResizeObserver: ResizeObserver | null = null

function distanceFromBottom(el: HTMLElement) {
  return el.scrollHeight - el.scrollTop - el.clientHeight
}

function isNearBottom(el: HTMLElement) {
  return distanceFromBottom(el) <= NEAR_BOTTOM_PX
}

function onChatBodyScroll() {
  const el = chatBodyRef.value
  if (!el) return
  if (performance.now() < ignoreScrollUntil) return
  stickToBottom.value = isNearBottom(el)
}

/** 同步贴底：在布局之后、绘制之前写入 scrollTop，避免「先露一帧再回弹」 */
function pinToBottom() {
  const el = chatBodyRef.value
  if (!el || !stickToBottom.value) return
  const top = Math.max(0, el.scrollHeight - el.clientHeight)
  if (el.scrollTop === top) return
  ignoreScrollUntil = performance.now() + 120
  el.scrollTop = top
}

function schedulePinToBottom() {
  if (scrollRaf) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0
    pinToBottom()
  })
}

/** 贴底滚动：仅在已接近底部时跟随；即时 scrollTop，禁止 smooth / scrollIntoView */
async function scrollToBottom(force = false) {
  if (force) stickToBottom.value = true
  await nextTick()
  pinToBottom()
  schedulePinToBottom()
}

watch(
  messageListRef,
  (el) => {
    listResizeObserver?.disconnect()
    listResizeObserver = null
    if (!el || typeof ResizeObserver === 'undefined') return
    // ResizeObserver 在绘制前触发：同步贴底，不再经 rAF 晚一帧
    listResizeObserver = new ResizeObserver(() => {
      if (stickToBottom.value) pinToBottom()
    })
    listResizeObserver.observe(el)
  },
  { flush: 'post' },
)

onMounted(() => {
  void bootstrap.ensureLoaded()
  chatBodyRef.value?.addEventListener('scroll', onChatBodyScroll, { passive: true })
})

onBeforeUnmount(() => {
  chatBodyRef.value?.removeEventListener('scroll', onChatBodyScroll)
  listResizeObserver?.disconnect()
  listResizeObserver = null
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
  if (highlightTimer) window.clearTimeout(highlightTimer)
})

watch(
  () => chatStore.sessionViewEpoch,
  () => {
    void scrollToBottom(true)
  },
)

watch(
  () => chatStore.messages.length,
  () => {
    if (stickToBottom.value) pinToBottom()
  },
)

watch(
  () => chatStore.isLoading,
  (loading) => {
    if (loading) void scrollToBottom(true)
  },
)
</script>

<style lang="scss" scoped>
@import '@/styles/mixins.scss';

.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  min-width: 0;
  min-height: 0;
  max-width: 100%;
  box-sizing: border-box;
  position: relative;
  overflow: hidden;
  overflow-x: hidden;
  overscroll-behavior: none;
  z-index: 1;
  background: transparent;

  // 中间聊天区 + 右侧提问目录
  .chat-stage {
    flex: 1 1 auto;
    display: flex;
    flex-direction: row;
    min-height: 0;
    min-width: 0;
    max-width: 100%;
    overflow: hidden;
    padding-right: 4px;
    box-sizing: border-box;
  }

  // 中间聊天区：滚动只发生在此。
  // 宽度恒定策略：overflow-y: scroll（始终可滚）+ 隐藏条（条宽 0，不吃布局），不依赖 scrollbar-gutter: stable
  .chat-body {
    flex: 1 1 auto;
    min-height: 0;
    min-width: 0;
    max-width: 100%;
    overflow-x: hidden;
    overflow-y: scroll;
    padding: 16px 24px 12px;
    scroll-behavior: auto;
    overflow-anchor: none;
    overscroll-behavior: contain;
    position: relative;
    z-index: 1;
    contain: layout style;
    @include hide-scrollbar;
  }

  // 消息列表容器：占满中间内容区宽度（与 chat-body 内边距内可用宽度一致）
  .message-list {
    display: flex;
    flex-direction: column;
    width: 100%;
    max-width: 100%;
    margin: 0;
    min-height: 200px;
    min-width: 0;
    box-sizing: border-box;
  }

  .chat-dock {
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    max-width: 100%;
    min-width: 0;
    padding: 0 24px 8px;
    box-sizing: border-box;
    position: relative;
    z-index: 2;
    overflow: hidden;
  }

  .next-steps {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
    max-width: 100%;
  }

  .next-steps-label {
    font-size: var(--font-size-md);
    color: var(--text-muted, #94a3b8);
  }

  .next-steps-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    max-height: 80px;
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior: contain;
    @include hide-scrollbar;
  }

  .next-step-chip {
    max-width: 100%;
    padding: 4px 10px;
    border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.2));
    border-radius: 999px;
    background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58));
    color: var(--text-primary);
    font-size: var(--font-size-md);
    line-height: var(--line-height-normal);
    text-align: left;
    cursor: pointer;
    transition: border-color 0.2s ease, box-shadow 0.2s ease, color 0.2s ease;

    &:hover {
      border-color: rgba(56, 102, 245, 0.18);
      color: #3866f5;
      box-shadow: 0 0 0 3px rgba(56, 102, 245, 0.04);
    }
  }

  // 紧跟消息列表末尾，水平居中；固定行高，避免用时文案变长时换行抖动
  .session-stats {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    justify-content: center;
    gap: 6px 14px;
    width: 100%;
    height: 36px;
    min-height: 36px;
    max-height: 36px;
    margin: 6px 0 0;
    padding: 6px 4px 2px;
    font-size: var(--font-size-xs);
    line-height: 1.4;
    color: var(--text-muted);
    text-align: center;
    border-top: 1px dashed var(--chat-glass-border, rgba(15, 23, 42, 0.06));
    box-sizing: border-box;
    overflow: hidden;

    span {
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }
  }
}
</style>
