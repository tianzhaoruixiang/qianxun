<template>
  <div
    class="message-item"
    :class="[`message-${message.role}`, { 'is-highlighted': highlighted }]"
    :data-message-id="message.id"
  >
    <div class="bubble-wrapper" :class="{ 'wrapper-assistant': message.role === 'assistant' }">
      <!-- AI 消息头像 -->
      <div
        v-if="message.role === 'assistant'"
        class="avatar-small avatar-robot"
        :class="{ 'avatar-streaming': streamActive }"
      >
        <span v-if="streamActive" class="avatar-stream-halo" aria-hidden="true">
          <span class="avatar-stream-glow"></span>
        </span>
        <span class="avatar-core">
          <AgentAvatar
            :group-key="chatAvatar.groupKey"
            :label="chatAvatar.label"
            :icon="chatAvatar.icon"
            size="md"
          />
        </span>
      </div>

      <!-- 用户消息头像 -->
      <div v-if="message.role === 'user'" class="avatar-small avatar-user">
        <img :src="userImg" alt="用户头像" />
      </div>

      <div class="bubble-content">
        <div
          v-if="message.role === 'user'"
          class="bubble bubble-user"
        >
          {{ message.content }}
          <div v-if="message.attachments?.length" class="user-attachments">
            <span
              v-for="a in message.attachments"
              :key="a.id"
              class="att-chip"
              :title="a.name"
            >{{ a.name }}</span>
          </div>
        </div>
        <div
          v-else
          class="assistant-thread"
          :class="{ 'thread-streaming': streamActive }"
        >
          <span v-if="isTyping && !streamActive" class="typing-indicator" aria-label="智能体正在准备回复">
            <span></span><span></span><span></span>
          </span>
          <div
            v-else-if="showPendingStages"
            class="pending-stages"
            aria-live="polite"
            :aria-label="pendingStageText"
          >
            <span class="thinking-spinner" aria-hidden="true"></span>
            <span class="pending-stage-text">{{ pendingStageText }}</span>
          </div>
          <template v-else>
            <div v-if="hasThinking" class="thinking-block" :class="{ collapsed: thinkingCollapsed, streaming: streamActive }">
              <button
                type="button"
                class="thinking-toggle"
                :class="{ collapsed: thinkingCollapsed, streaming: streamActive }"
                :aria-expanded="!thinkingCollapsed"
                :aria-label="thinkingCollapsed ? '展开思考过程' : '收起思考过程'"
                @click="thinkingCollapsed = !thinkingCollapsed"
              >
                <span class="thinking-spinner" v-if="streamActive" aria-hidden="true"></span>
                <span class="thinking-label">{{ thinkingTitle }}</span>
                <span class="thinking-meta">{{ thinkingMeta }}</span>
                <span class="thinking-chevron" aria-hidden="true">{{ thinkingCollapsed ? '▸' : '▾' }}</span>
              </button>
            </div>

            <template v-for="(part, idx) in timelineParts" :key="part.key">
              <template v-if="!isThinkingPart(idx) || !thinkingCollapsed">
              <p
                v-if="hasThinking && idx === firstAnswerIndex"
                class="answer-kicker"
              >回答</p>
              <div
                v-if="part.kind === 'text'"
                class="bubble bubble-assistant"
                :class="{
                  'bubble-streaming': streamActive && idx === timelineParts.length - 1,
                  'bubble-thinking': isThinkingPart(idx),
                  'thinking-part': isThinkingPart(idx),
                  'answer-part': !isThinkingPart(idx),
                }"
              >
                <template v-if="isThinkingPart(idx)">
                  <MarkdownContent :preview-id="`md-${message.id}-${part.key}`" :content="part.text" />
                </template>
                <template v-else>
                  <MarkdownContent
                    v-if="answerSplits[part.key]?.conclusion"
                    :preview-id="`md-${message.id}-${part.key}`"
                    :content="answerSplits[part.key].conclusion"
                  />
                  <AssistantAppendix
                    v-if="answerSplits[part.key]?.hasAppendix"
                    :preview-id="`md-${message.id}-${part.key}-appendix`"
                    :content="answerSplits[part.key].appendix"
                    :stream-active="streamActive && idx === timelineParts.length - 1"
                  />
                </template>
              </div>
              <SubagentCrew
                v-else-if="part.kind === 'parallel'"
                class="tool-in-thinking thinking-part"
                :tools="part.tools"
                :all-tools="message.toolCalls"
                :message-id="message.id"
                :active-tool-key="activeToolKey"
                @open-tool="emit('open-tool', $event)"
              />
              <SubagentCrew
                v-else-if="isSubagentTool(part.tool)"
                :class="{ 'tool-in-thinking': isThinkingPart(idx), 'thinking-part': isThinkingPart(idx), 'answer-part': !isThinkingPart(idx) }"
                :tools="[part.tool]"
                :all-tools="message.toolCalls"
                :message-id="message.id"
                :active-tool-key="activeToolKey"
                @open-tool="emit('open-tool', $event)"
              />
              <div
                v-else
                class="tool-panel"
                :class="[
                  toolStatusClass(part.tool),
                  {
                    'tool-in-thinking': isThinkingPart(idx),
                    'thinking-part': isThinkingPart(idx),
                    'answer-part': !isThinkingPart(idx),
                    'tool-active': isToolActive(part.key),
                  },
                ]"
              >
                <button
                  type="button"
                  class="tool-row"
                  :class="[toolStatusClass(part.tool), { active: isToolActive(part.key) }]"
                  :title="toolRowTitle(part.tool)"
                  :aria-pressed="isToolActive(part.key)"
                  @click="onToolRowClick(part)"
                >
                  <ToolCallIcon :kind="toolIconKind(part.tool)" :status="toolStatusClass(part.tool)" />
                  <span class="tool-name">{{ formatToolName(part.tool) }}</span>
                  <span class="tool-desc">{{ toolDescription(part.tool) || '—' }}</span>
                  <span class="tool-status" :class="toolStatusClass(part.tool)">
                    <span class="tool-status-glyph" aria-hidden="true">
                      <span v-if="isToolRunning(part.tool)" class="status-spin"></span>
                      <span v-else-if="isToolError(part.tool)" class="status-error"></span>
                      <span v-else class="status-done"></span>
                    </span>
                    {{ toolStatusLabel(part.tool) }}
                  </span>
                  <span class="tool-time">{{ formatToolDuration(part.tool) }}</span>
                  <span class="tool-chevron" aria-hidden="true">›</span>
                </button>
                <SubagentCrew
                  v-if="useParallelChildren(part.tool)"
                  class="nested-crew"
                  :tools="childrenOf(part.tool)"
                  :all-tools="message.toolCalls"
                  :message-id="message.id"
                  :active-tool-key="activeToolKey"
                  @open-tool="emit('open-tool', $event)"
                />
                <div v-else class="tool-children">
                  <button
                    v-for="(child, ci) in childrenOf(part.tool)"
                    :key="child.toolCallId || `child-${ci}`"
                    type="button"
                    class="tool-row tool-row-child"
                    :class="[toolStatusClass(child), { active: isToolActive(child.toolCallId || `child-${ci}`) }]"
                    :title="toolRowTitle(child)"
                    @click="onToolRowClick({ key: child.toolCallId || `child-${ci}`, tool: child })"
                  >
                    <ToolCallIcon :kind="toolIconKind(child)" :status="toolStatusClass(child)" />
                    <span class="tool-name">{{ formatToolName(child) }}</span>
                    <span class="tool-desc">{{ toolDescription(child) || '—' }}</span>
                    <span class="tool-status" :class="toolStatusClass(child)">
                      <span class="tool-status-glyph" aria-hidden="true">
                        <span v-if="isToolRunning(child)" class="status-spin"></span>
                        <span v-else-if="isToolError(child)" class="status-error"></span>
                        <span v-else class="status-done"></span>
                      </span>
                      {{ toolStatusLabel(child) }}
                    </span>
                    <span class="tool-time">{{ formatToolDuration(child) }}</span>
                  </button>
                </div>
              </div>
              </template>
            </template>
          </template>
          <!-- 入库链接是完整 token，流式过程中也可展示下载卡 -->
          <ChatDocumentCards v-if="message.content" :content="message.content" />
          <div
            v-if="streamActive && !showPendingStages"
            class="streaming-footer"
            aria-live="polite"
            :aria-label="message.toolCalling ? '正在调用工具' : '智能体正在输出'"
          >
            <span class="streaming-spinner" aria-hidden="true"></span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { Message, ToolCallInfo } from '@/types/chat'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import MarkdownContent from '@/components/MarkdownContent.vue'
import AssistantAppendix from '@/components/AssistantAppendix.vue'
import ChatDocumentCards from '@/components/ChatDocumentCards.vue'
import { splitAssistantContent } from '@/utils/splitAssistantContent'
import ToolCallIcon from '@/components/ToolCallIcon.vue'
import { resolveToolIconKind } from '@/utils/toolIcon'
import { toolCallFailure, toolCallSummaryLine } from '@/utils/toolDetailAdapt'
import {
  descendantsOf,
  effectiveStatusClass,
  effectiveToolStatus,
  isSubagentTool,
} from '@/utils/subagentTools'
import AgentAvatar from '@/components/AgentAvatar.vue'
import SubagentCrew from '@/components/SubagentCrew.vue'
import { useChatAgentAvatar } from '@/composables/useChatAgentAvatar'
import { brandCopy } from '@/utils/brandCopy'
import userImg from '@/assets/images/portraits/user-officer.webp'

const props = defineProps<{
  message: Message
  /** 当前是否为列表中最后一条且仍在流式生成（由父组件结合 isLoading 传入） */
  streamActive?: boolean
  /** 右侧详情抽屉当前选中的工具 key（messageId 内唯一） */
  activeToolKey?: string | null
  /** 从提问目录跳转后高亮该条用户提问 */
  highlighted?: boolean
}>()

const emit = defineEmits<{
  'open-tool': [payload: { messageId: string; toolKey: string; tool: ToolCallInfo }]
}>()

const bootstrap = useBootstrapStore()
const chatAvatar = useChatAgentAvatar()
/** 思考过程默认收起；用户可手动展开，流式结束后仍保持收起 */
const thinkingCollapsed = ref(true)

onMounted(() => {
  void bootstrap.ensureLoaded()
})

watch(
  () => props.streamActive,
  (active, prev) => {
    if (prev === true && active === false) {
      thinkingCollapsed.value = true
    }
  },
)

type TimelinePart =
  | { kind: 'text'; key: string; text: string }
  | { kind: 'tool'; key: string; tool: ToolCallInfo }
  | { kind: 'parallel'; key: string; tools: ToolCallInfo[] }

const timelineParts = computed<TimelinePart[]>(() => {
  const content = props.message.content || ''
  const tools = [...(props.message.toolCalls ?? [])].filter((t) => !t.parentId)
  if (!tools.length) {
    return content ? [{ kind: 'text', key: 'text-0', text: content }] : []
  }
  tools.sort((a, b) => {
    const ao = a.contentOffset ?? Number.MAX_SAFE_INTEGER
    const bo = b.contentOffset ?? Number.MAX_SAFE_INTEGER
    if (ao !== bo) return ao - bo
    return (a.startedAt ?? 0) - (b.startedAt ?? 0)
  })
  const parts: TimelinePart[] = []
  let cursor = 0
  let i = 0
  while (i < tools.length) {
    const tool = tools[i]
    const at = Math.min(Math.max(tool.contentOffset ?? cursor, 0), content.length)
    if (at > cursor) {
      parts.push({ kind: 'text', key: `text-${cursor}`, text: content.slice(cursor, at) })
      cursor = at
    }
    if (isSubagentTool(tool)) {
      const group: ToolCallInfo[] = [tool]
      let j = i + 1
      while (j < tools.length && isSubagentTool(tools[j])) {
        const nextAt = Math.min(Math.max(tools[j].contentOffset ?? cursor, 0), content.length)
        if (nextAt > cursor) break
        group.push(tools[j])
        j += 1
      }
      if (group.length > 1) {
        parts.push({
          kind: 'parallel',
          key: `parallel-${group.map((g) => g.toolCallId || g.startedAt).join('-')}`,
          tools: group,
        })
        i = j
        continue
      }
    }
    parts.push({ kind: 'tool', key: tool.toolCallId || `tool-${i}`, tool })
    i += 1
  }
  if (cursor < content.length) {
    parts.push({ kind: 'text', key: `text-${cursor}`, text: content.slice(cursor) })
  }
  return parts
})

/** 最后一个工具调用在时间线中的下标；无工具则为 -1 */
const lastToolPartIndex = computed(() => {
  const parts = timelineParts.value
  for (let i = parts.length - 1; i >= 0; i -= 1) {
    const p = parts[i]
    if (p?.kind === 'tool' || p?.kind === 'parallel') return i
  }
  return -1
})

const hasThinking = computed(() => lastToolPartIndex.value >= 0)

const firstAnswerIndex = computed(() => {
  const last = lastToolPartIndex.value
  return last < 0 ? 0 : last + 1
})

function isThinkingPart(idx: number): boolean {
  const last = lastToolPartIndex.value
  return last >= 0 && idx <= last
}

const answerSplits = computed(() => {
  const map: Record<string, ReturnType<typeof splitAssistantContent>> = {}
  timelineParts.value.forEach((part, idx) => {
    if (part.kind === 'text' && !isThinkingPart(idx)) {
      map[part.key] = splitAssistantContent(part.text)
    }
  })
  return map
})

const thinkingToolCount = computed(() => {
  return timelineParts.value.reduce((n, p) => {
    if (p.kind === 'tool') return n + 1
    if (p.kind === 'parallel') return n + p.tools.length
    return n
  }, 0)
})

const thinkingTitle = computed(() => {
  if (props.streamActive) {
    const running = (props.message.toolCalls ?? []).filter(
      (t) => !t.parentId && isSubagentTool(t)
        && (effectiveToolStatus(t, props.message.toolCalls) === 'running'
          || effectiveToolStatus(t, props.message.toolCalls) === 'awaiting'),
    )
    if (running.length > 1) return `${running.length} 个子智能体并行中`
    const awaiting = (props.message.toolCalls ?? []).some((t) => {
      const s = (t.status || '').toLowerCase()
      return s === 'awaiting' || s === 'background'
    })
    return awaiting ? '子智能体执行中' : '思考中'
  }
  return '已思考'
})

const thinkingMeta = computed(() => {
  const n = thinkingToolCount.value
  const toolLabel = n > 0 ? `${n} 个工具` : ''
  if (props.streamActive) {
    return toolLabel ? `${toolLabel} · 进行中` : '进行中'
  }
  return toolLabel
})

/** 是否正在打字（AI 流式响应中） */
const isTyping = computed(() => {
  return (
    props.message.role === 'assistant'
    && props.message.content === ''
    && !timelineParts.value.length
  )
})

/** 流式已开始、尚无正文/工具卡片：展示阶段性假加载，避免空白等待 */
const showPendingStages = computed(() => Boolean(props.streamActive) && isTyping.value)

const PENDING_STAGE_INTERVAL_MS = 3000
const pendingStageIndex = ref(0)
let pendingStageTimer: ReturnType<typeof setInterval> | null = null

const pendingStageText = computed(() => {
  const stages = brandCopy.pendingReplyStages
  if (!stages.length) return ''
  return stages[pendingStageIndex.value % stages.length]
})

function stopPendingStages() {
  if (pendingStageTimer != null) {
    clearInterval(pendingStageTimer)
    pendingStageTimer = null
  }
}

function startPendingStages() {
  stopPendingStages()
  pendingStageIndex.value = 0
  pendingStageTimer = setInterval(() => {
    pendingStageIndex.value = (pendingStageIndex.value + 1) % brandCopy.pendingReplyStages.length
  }, PENDING_STAGE_INTERVAL_MS)
}

watch(
  showPendingStages,
  (show) => {
    if (show) startPendingStages()
    else stopPendingStages()
  },
  { immediate: true },
)

onUnmounted(() => {
  stopPendingStages()
})

function childrenOf(tool: ToolCallInfo): ToolCallInfo[] {
  return descendantsOf(tool, props.message.toolCalls)
}

function useParallelChildren(tool: ToolCallInfo): boolean {
  const kids = childrenOf(tool)
  return kids.length > 1 && kids.every((c) => isSubagentTool(c))
}

function formatToolName(tool: ToolCallInfo): string {
  const fromServer = tool.displayName?.trim()
  if (fromServer) return fromServer
  return bootstrap.toolLabel(tool.toolName)
}

function toolIconKind(tool: ToolCallInfo): string {
  return resolveToolIconKind(tool)
}

function isToolRunning(tool: ToolCallInfo): boolean {
  const s = effectiveToolStatus(tool, props.message.toolCalls)
  return s === 'running' || s === 'awaiting'
}

function isToolAwaiting(tool: ToolCallInfo): boolean {
  return effectiveToolStatus(tool, props.message.toolCalls) === 'awaiting'
}

function isToolError(tool: ToolCallInfo): boolean {
  return effectiveToolStatus(tool, props.message.toolCalls) === 'error' || !!toolCallFailure(tool)
}

function toolStatusClass(tool: ToolCallInfo): 'running' | 'completed' | 'error' {
  return effectiveStatusClass(tool, props.message.toolCalls)
}

function toolStatusLabel(tool: ToolCallInfo): string {
  const s = effectiveToolStatus(tool, props.message.toolCalls)
  if (s === 'error') return '失败'
  if (s === 'awaiting') return '后台执行中'
  if (s === 'running') return '进行中'
  return '成功'
}

function toolDescription(tool: ToolCallInfo): string {
  return toolCallSummaryLine(tool, 120)
}

function formatDurationMs(ms: number): string {
  if (ms < 1000) return `${Math.max(0, Math.round(ms))}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatToolDuration(tool: ToolCallInfo): string {
  if (isToolAwaiting(tool)) return '等待中'
  if (isToolRunning(tool)) return '…'
  const dur = tool.durationMs
    ?? (tool.durationSeconds != null ? Math.round(tool.durationSeconds * 1000) : undefined)
    ?? (tool.startedAt && tool.endedAt ? Math.max(0, tool.endedAt - tool.startedAt) : undefined)
  if (dur == null) return '—'
  return formatDurationMs(dur)
}

function toolRowTitle(tool: ToolCallInfo): string {
  const name = formatToolName(tool)
  const desc = toolDescription(tool)
  const time = formatToolDuration(tool)
  return [name, toolStatusLabel(tool), desc, time, '点击查看执行详情'].filter(Boolean).join('\n')
}

function isToolActive(key: string): boolean {
  return !!props.activeToolKey && props.activeToolKey === key
}

function onToolRowClick(part: { key: string; tool: ToolCallInfo }) {
  emit('open-tool', {
    messageId: props.message.id,
    toolKey: part.key,
    tool: part.tool,
  })
}
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';

.message-item {
  display: flex;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
  margin-bottom: 16px;
  // 纵向交给 .chat-body；勿建滚动容器（否则回答区易再出滚动条并挤宽抖动）
  overflow-x: clip;
  overflow-y: visible;
  overflow-anchor: none;
  // 不在挂载时做入场动画：切换历史会话会批量 remount，fade-in 会像整页刷新抖动

  // 用户消息 - 右对齐
  &.message-user {
    justify-content: flex-end;

    .bubble-wrapper {
      flex-direction: row-reverse;
    }
  }

  &.is-highlighted.message-user .bubble-user {
    outline: 2px solid rgba(255, 255, 255, 0.95);
    box-shadow:
      0 0 0 3px rgba(56, 102, 245, 0.55),
      0 2px 16px rgba(54, 169, 255, 0.45);
    animation: question-highlight 1.2s ease;
  }

  @keyframes question-highlight {
    0% {
      transform: scale(1.02);
      box-shadow:
        0 0 0 6px rgba(56, 102, 245, 0.35),
        0 2px 16px rgba(54, 169, 255, 0.45);
    }
    100% {
      transform: scale(1);
    }
  }

  // AI 消息 - 左对齐
  &.message-assistant {
    justify-content: flex-start;

    .bubble-wrapper {
      width: min(100%, calc(100% - 40px - 10px));
    }
  }

  .bubble-wrapper {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    min-width: 0;
    max-width: min(100%, calc(100% - 40px - 10px));
    box-sizing: border-box;

    // 小头像
    .avatar-small {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      overflow: hidden;
      flex-shrink: 0;
      box-sizing: border-box;
      box-shadow: 0 2px 8px rgba(14, 165, 233, 0.2);

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
      }

      // 用户头像样式
      &.avatar-user {
        width: 40px;
        height: 40px;
        background: #c5cdd6;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-top: 6px;
        padding: 0;
        overflow: hidden;

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
          object-position: center 22%;
          display: block;
        }
      }

      // AI机器人头像：固定 48×48，呼吸仅 transform/opacity，overflow 裁切避免撑出滚动条
      &.avatar-robot {
        position: relative;
        width: 48px;
        height: 48px;
        background: var(--chat-glass-strong, rgba(255, 255, 255, 0.78));
        border: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
        display: flex;
        align-items: center;
        justify-content: center;
        margin-top: 6px;
        padding: 0;
        overflow: hidden;
        isolation: isolate;
        contain: layout style paint;
        flex-shrink: 0;

        .avatar-core {
          position: relative;
          z-index: 1;
          display: flex;
          align-items: center;
          justify-content: center;
          width: 48px;
          height: 48px;
          border-radius: 50%;
          overflow: hidden;
          transform-origin: center center;
          will-change: transform;
        }

        :deep(.agent-avatar) {
          width: 48px;
          height: 48px;
        }
      }

      &.avatar-streaming {
        z-index: 1;

        .avatar-core {
          animation: avatar-core-breathe 2.2s ease-in-out infinite;
        }
      }

      .avatar-stream-halo {
        position: absolute;
        inset: 0;
        z-index: 0;
        overflow: hidden;
        pointer-events: none;
        border-radius: 50%;
      }

      .avatar-stream-glow {
        position: absolute;
        inset: 0;
        border-radius: 50%;
        background: radial-gradient(
          circle at center,
          rgba(56, 102, 245, 0.28) 0%,
          rgba(54, 169, 255, 0.14) 48%,
          rgba(54, 169, 255, 0) 72%
        );
        pointer-events: none;
        transform-origin: center center;
        will-change: opacity;
        animation: avatar-stream-pulse 2.2s ease-in-out infinite;
      }
    }

    .bubble-content {
      display: flex;
      flex-direction: column;
      flex: 1 1 auto;
      min-width: 0;
      max-width: 100%;
      overflow-x: clip;
      overflow-y: visible;

      .assistant-thread {
        display: flex;
        flex-direction: column;
        gap: 8px;
        min-width: 0;
        max-width: 100%;
        overflow: visible;
      }

      .pending-stages {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        min-height: 22px;
        padding: 0 2px;
        color: #2563eb;
      }

      .pending-stage-text {
        font-size: var(--font-size-xs);
        font-weight: var(--font-weight-medium, 500);
        line-height: 1.25;
        color: inherit;
      }

      .thinking-block {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 0;
        max-width: 100%;
      }

      .thinking-toggle {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        width: fit-content;
        max-width: 100%;
        min-height: 22px;
        margin: 0;
        padding: 0 2px;
        border: 0;
        border-radius: 0;
        background: transparent;
        box-shadow: none;
        cursor: pointer;
        font: inherit;
        color: var(--text-muted);
        transition: color 0.15s ease;

        &:hover {
          color: var(--text-secondary);
        }

        &.streaming {
          color: #2563eb;
        }

        &.collapsed {
          color: var(--text-muted);
        }
      }

      .thinking-spinner {
        flex-shrink: 0;
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: conic-gradient(
          from 180deg,
          rgba(56, 102, 245, 0) 0deg,
          rgba(54, 169, 255, 0.2) 80deg,
          #36a9ff 200deg,
          #3866f5 300deg,
          rgba(56, 102, 245, 0) 360deg
        );
        -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 1.9px));
        mask: radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 1.9px));
        animation: tool-status-spin 0.85s linear infinite;
      }

      .thinking-label {
        flex-shrink: 0;
        font-size: var(--font-size-xs);
        font-weight: var(--font-weight-medium, 500);
        line-height: 1.25;
        color: inherit;
      }

      .thinking-meta {
        min-width: 0;
        font-size: var(--font-size-xs);
        line-height: 1.25;
        color: inherit;
        opacity: 0.78;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .thinking-chevron {
        flex-shrink: 0;
        margin-left: 0;
        font-size: 10px;
        color: inherit;
        line-height: 1;
        opacity: 0.7;
      }

      .thinking-part {
        max-width: 100%;
        margin-left: 2px;
        padding-left: 10px;
        border-left: 2px solid rgba(148, 163, 184, 0.38);
        box-sizing: border-box;
      }

      .bubble-thinking {
        padding-top: 4px;
        padding-right: 0;
        padding-bottom: 4px;
        background: transparent;
        border: 0;
        border-radius: 0;
        box-shadow: none;
        font-size: var(--font-size-sm);
        line-height: 1.55;
        color: var(--text-secondary);
      }

      .tool-panel.tool-in-thinking {
        opacity: 1;
        background: rgba(248, 250, 252, 0.72);
        border-color: rgba(148, 163, 184, 0.16);
        box-shadow: none;
      }

      .answer-kicker {
        margin: 6px 0 0;
        padding: 0;
        font-size: 11px;
        font-weight: var(--font-weight-semibold);
        letter-spacing: 0.08em;
        color: var(--text-muted);
        line-height: 1;
      }

      .answer-part {
        margin-top: 0;
      }

      .tool-panel {
        display: flex;
        flex-direction: column;
        gap: 0;
        max-width: 100%;
        min-width: 0;
        border-radius: 10px;
        background: var(--chat-glass, rgba(255, 255, 255, 0.52));
        border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.2));
        box-sizing: border-box;
        overflow: hidden;

        &.running {
          border-color: rgba(56, 102, 245, 0.12);
          background: rgba(56, 102, 245, 0.04);
        }

        &.completed {
          border-color: rgba(22, 163, 74, 0.12);
          background: rgba(22, 163, 74, 0.04);
        }

        &.error {
          border-color: rgba(220, 38, 38, 0.12);
          background: rgba(220, 38, 38, 0.04);
        }

        &.tool-active {
          border-color: rgba(56, 102, 245, 0.28);
          box-shadow: 0 0 0 1px rgba(56, 102, 245, 0.12);
        }
      }

      .nested-crew {
        padding: 0 8px 8px;
      }

      .tool-children {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }

      .tool-row {
        display: flex;
        flex-wrap: nowrap;
        align-items: center;
        gap: 8px;
        width: 100%;
        max-width: 100%;
        min-width: 0;
        height: 32px;
        min-height: 32px;
        max-height: 32px;
        padding: 0 12px;
        margin: 0;
        border: 0;
        border-radius: 0;
        background: transparent;
        box-sizing: border-box;
        overflow: hidden;
        cursor: pointer;
        text-align: left;
        font: inherit;
        color: inherit;

        &:hover {
          background: rgba(15, 23, 42, 0.03);

          :deep(.ag-icon) {
            transform: none;
            filter: none;
          }
        }

        &.tool-row-child {
          padding-left: 28px;
          height: 28px;
          min-height: 28px;
          max-height: 28px;
          opacity: 0.92;
        }

        &.active {
          background: rgba(56, 102, 245, 0.08);
        }

        &.running,
        &.completed,
        &.error {
          background: transparent;
          border: 0;

          &.active {
            background: rgba(56, 102, 245, 0.08);
          }
        }
      }

      .tool-name {
        flex: 0 1 auto;
        max-width: 28%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--font-size-sm);
        font-weight: var(--font-weight-semibold);
        color: var(--text-primary);
      }

      .tool-desc {
        display: block;
        flex: 1 1 auto;
        min-width: 0;
        max-width: 100%;
        font-size: var(--font-size-sm);
        color: var(--text-secondary);
        line-height: var(--line-height-tight);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .tool-status {
        display: inline-flex;
        flex: 0 0 auto;
        align-items: center;
        gap: 4px;
        font-size: var(--font-size-xs);
        color: var(--text-muted);
        white-space: nowrap;

        &.running {
          color: #2563eb;
        }

        &.completed {
          color: #16a34a;
        }

        &.error {
          color: #dc2626;
        }
      }

      .tool-status-glyph {
        width: var(--icon-size-sm);
        height: var(--icon-size-sm);
        display: inline-flex;
        flex-shrink: 0;
        align-items: center;
        justify-content: center;
        line-height: 0;

        .status-spin {
          width: var(--icon-size-xs);
          height: var(--icon-size-xs);
          border-radius: 50%;
          background: conic-gradient(
            from 180deg,
            rgba(56, 102, 245, 0) 0deg,
            rgba(54, 169, 255, 0.2) 80deg,
            #36a9ff 200deg,
            #3866f5 300deg,
            rgba(56, 102, 245, 0) 360deg
          );
          -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 1.9px));
          mask: radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 1.9px));
          animation: tool-status-spin 0.85s linear infinite;
        }

        .status-done {
          width: var(--icon-size-xs);
          height: var(--icon-size-xs);
          border-radius: 50%;
          background: linear-gradient(180deg, #22c55e, #16a34a);
          box-shadow: 0 0 0 2px rgba(22, 163, 74, 0.16);

          &::after {
            content: '';
            display: block;
            width: 3.5px;
            height: 6.5px;
            margin: 2px auto 0;
            border-right: 1.5px solid #fff;
            border-bottom: 1.5px solid #fff;
            transform: rotate(45deg);
          }
        }

        .status-error {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #dc2626;
          box-shadow: 0 0 0 2px rgba(220, 38, 38, 0.18);
        }
      }

      .tool-time {
        flex: 0 0 3.6em;
        width: 3.6em;
        text-align: right;
        font-size: var(--font-size-xs);
        font-variant-numeric: tabular-nums;
        color: var(--text-muted);
        white-space: nowrap;
      }

      .tool-chevron {
        flex: 0 0 auto;
        font-size: 16px;
        font-weight: 500;
        color: var(--text-muted);
        line-height: 1;
        opacity: 0.7;
      }

      // 气泡通用样式
      .bubble {
        max-width: 100%;
        min-width: 0;
        box-sizing: border-box;
        padding: 12px 16px;
        border-radius: var(--radius-md);
        font-size: var(--font-size-md);
        line-height: var(--line-height-relaxed);
        letter-spacing: var(--letter-spacing-body);
        word-break: break-word;
        overflow-wrap: anywhere;
      }

      // 用户气泡
      .bubble-user {
        white-space: pre-wrap;
        background: linear-gradient(0deg, rgba(0, 0, 0, 0), rgba(0, 0, 0, 0)), linear-gradient(76deg, #3866F5 -8%, #36A9FF 81%);
        color: white;
        border-bottom-right-radius: 4px;
        box-shadow: 0 2px 12px rgba(14, 165, 233, 0.25);

        .user-attachments {
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
          margin-top: 8px;
        }

        .att-chip {
          display: inline-block;
          max-width: 220px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          padding: 2px 8px;
          border-radius: 999px;
          background: rgba(255, 255, 255, 0.18);
          font-size: var(--font-size-xs);
        }
      }

      // AI 气泡
      .bubble-assistant {
        min-width: 0;
        overflow: visible;
        background: var(--chat-glass-strong, rgba(255, 255, 255, 0.78));
        color: var(--text-primary);
        border: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
        border-bottom-left-radius: 4px;
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55), var(--shadow-sm);

        &.bubble-streaming {
          border-color: var(--border-subtle, rgba(148, 163, 184, 0.2));
          box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55), var(--shadow-sm);
        }

        &.answer-part {
          padding: 14px 18px;
        }
      }

      .streaming-footer {
        display: flex;
        align-items: center;
        margin-top: 10px;
        min-height: 18px;
        height: 18px;
        flex-shrink: 0;
      }

      .assistant-thread.thread-streaming:not(:has(.bubble)):not(:has(.tool-panel)) .streaming-footer {
        margin-top: 6px;
        min-height: 40px;
        height: 40px;
      }

      .streaming-spinner {
        width: 18px;
        height: 18px;
        border-radius: 50%;
        background: conic-gradient(
          from 180deg,
          rgba(56, 102, 245, 0) 0deg,
          rgba(54, 169, 255, 0.15) 80deg,
          #36a9ff 200deg,
          #3866f5 300deg,
          rgba(56, 102, 245, 0) 360deg
        );
        -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 2.5px), #000 calc(100% - 2.4px));
        mask: radial-gradient(farthest-side, transparent calc(100% - 2.5px), #000 calc(100% - 2.4px));
        animation: stream-spin 0.75s linear infinite;
      }
    }

    // 打字机动画指示器
    .typing-indicator {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 0;

      span {
        width: 7px;
        height: 7px;
        background: var(--text-muted);
        border-radius: 50%;
        animation: typing-bounce 1.4s infinite ease-in-out both;

        &:nth-child(1) {
          animation-delay: 0s;
        }

        &:nth-child(2) {
          animation-delay: 0.16s;
        }

        &:nth-child(3) {
          animation-delay: 0.32s;
        }
      }
    }
  }
}

@keyframes stream-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes tool-status-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes avatar-core-breathe {
  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.04);
  }
}

@keyframes avatar-stream-pulse {
  0%,
  100% {
    opacity: 0.45;
  }

  50% {
    opacity: 0.95;
  }
}

</style>
