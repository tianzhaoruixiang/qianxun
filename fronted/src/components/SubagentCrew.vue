<template>
  <div class="subagent-crew" :class="{ 'is-multi': tools.length > 1 }">
    <div v-if="tools.length > 1" class="crew-banner">
      <div class="crew-faces" aria-hidden="true">
        <span
          v-for="(tool, i) in tools"
          :key="toolKey(tool)"
          class="crew-face"
          :class="{ running: isToolRunning(tool) }"
          :style="{ zIndex: tools.length - i, animationDelay: `${i * 0.12}s` }"
        >
          <AgentPortrait :icon="portraitId(tool)" :seed="portraitSeed(tool)" size="sm" />
        </span>
      </div>
      <div class="crew-copy">
        <span class="crew-title">{{ crewTitle }}</span>
        <span class="crew-meta">{{ crewMeta }}</span>
      </div>
    </div>

    <article
      v-for="tool in tools"
      :key="toolKey(tool)"
      class="mate-card"
      :class="[toolStatusClass(tool), { expanded: isExpanded(tool), active: isToolActive(toolKey(tool)) }]"
    >
      <header class="mate-head">
        <div class="mate-toggle">
          <button
            type="button"
            class="mate-open"
            :class="{ active: isToolActive(toolKey(tool)) }"
            :title="toolRowTitle(tool)"
            :aria-pressed="isToolActive(toolKey(tool))"
            @click="onOpen(toolKey(tool), tool)"
          >
            <span class="mate-avatar" :class="{ running: isToolRunning(tool) }">
              <AgentPortrait :icon="portraitId(tool)" :seed="portraitSeed(tool)" size="md" />
            </span>
            <span class="mate-identity">
              <span class="mate-name-row">
                <span class="mate-name">{{ mateName(tool) }}</span>
                <span class="mate-status" :class="toolStatusClass(tool)">
                  <span class="status-glyph" aria-hidden="true">
                    <span v-if="isToolRunning(tool)" class="status-spin"></span>
                    <span v-else-if="isToolError(tool)" class="status-error"></span>
                    <span v-else class="status-done"></span>
                  </span>
                  {{ toolStatusLabel(tool) }}
                </span>
              </span>
              <span class="mate-task">{{ toolDescription(tool) || formatToolName(tool) }}</span>
            </span>
            <span class="mate-time">{{ formatToolDuration(tool) }}</span>
          </button>
          <button
            type="button"
            class="mate-chevron-btn"
            :aria-expanded="isExpanded(tool)"
            :title="isExpanded(tool) ? '收起工具调用' : '展开工具调用'"
            @click="toggleExpand(tool)"
          >
            <span class="mate-chevron" aria-hidden="true">{{ isExpanded(tool) ? '▾' : '▸' }}</span>
          </button>
        </div>
      </header>

      <div v-if="isExpanded(tool)" class="mate-body">
        <p v-if="!childTools(tool).length" class="mate-empty">
          {{ isToolRunning(tool) ? '正在准备工具调用…' : '这次没有展开的工具调用记录' }}
        </p>
        <button
          v-for="(child, ci) in childTools(tool)"
          :key="child.toolCallId || `child-${ci}`"
          type="button"
          class="mate-tool"
          :class="[toolStatusClass(child), { active: isToolActive(childKey(child, ci)) }]"
          :title="toolRowTitle(child)"
          @click="onOpen(childKey(child, ci), child)"
        >
          <ToolCallIcon :kind="toolIconKind(child)" :status="toolStatusClass(child)" />
          <span class="mate-tool-name">{{ formatToolName(child) }}</span>
          <span class="mate-tool-desc">{{ toolDescription(child) || '—' }}</span>
          <span class="mate-tool-status" :class="toolStatusClass(child)">{{ toolStatusLabel(child) }}</span>
          <span class="mate-tool-time">{{ formatToolDuration(child) }}</span>
        </button>
        <button
          type="button"
          class="mate-open-detail"
          @click="onOpen(toolKey(tool), tool)"
        >
          查看子智能体详情
        </button>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, watch } from 'vue'
import type { ToolCallInfo } from '@/types/chat'
import AgentPortrait from '@/components/AgentPortrait.vue'
import ToolCallIcon from '@/components/ToolCallIcon.vue'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { resolveToolIconKind } from '@/utils/toolIcon'
import { toolCallFailure, toolCallSummaryLine } from '@/utils/toolDetailAdapt'
import {
  descendantsOf,
  effectiveStatusClass,
  effectiveStatusLabel,
  effectiveToolStatus,
  resolveSubagentMateName,
  subagentPortraitSeed,
  toolAgentCode,
} from '@/utils/subagentTools'
import { CIVILIAN_PORTRAIT_IDS, portraitIdForAgent } from '@/utils/agentPortraits'
import { looksLikeTechnicalId } from '@/utils/agentDisplay'
import { isDigitalOfficerDisplayName } from '@/utils/systemName'

const props = defineProps<{
  tools: ToolCallInfo[]
  allTools?: ToolCallInfo[]
  messageId: string
  activeToolKey?: string | null
}>()

const emit = defineEmits<{
  'open-tool': [payload: { messageId: string; toolKey: string; tool: ToolCallInfo }]
}>()

const bootstrap = useBootstrapStore()
const agentContext = useAgentContextStore()
const expanded = reactive<Record<string, boolean>>({})

onMounted(() => {
  void agentContext.ensureAgents()
})

const crewTitle = computed(() => {
  const running = props.tools.filter((t) => isToolRunning(t)).length
  if (running > 0) return `${props.tools.length} 位小助手正在协作`
  return `${props.tools.length} 位小助手已完成协作`
})

const crewMeta = computed(() => {
  const running = props.tools.filter((t) => isToolRunning(t)).length
  const failed = props.tools.filter((t) => isToolError(t)).length
  const bits = [`${props.tools.length} 个子智能体`]
  if (running) bits.push(`${running} 个进行中`)
  if (failed) bits.push(`${failed} 个失败`)
  return bits.join(' · ')
})

function toolKey(tool: ToolCallInfo): string {
  return tool.toolCallId || `tool-${tool.startedAt || 0}`
}

function childKey(tool: ToolCallInfo, index: number): string {
  return tool.toolCallId || `child-${index}`
}

function registeredAgent(tool: ToolCallInfo) {
  const code = toolAgentCode(tool)
  if (!code) return undefined
  return agentContext.agents.find((a) => a.code === code)
}

function portraitSeed(tool: ToolCallInfo): string {
  const code = toolAgentCode(tool)
  if (code) return code
  return subagentPortraitSeed(tool, toolKey(tool))
}

function portraitId(tool: ToolCallInfo): string {
  const agent = registeredAgent(tool)
  const code = toolAgentCode(tool)
  if (code) {
    return portraitIdForAgent(tool.agentIcon || agent?.icon, portraitSeed(tool))
  }
  return portraitIdForAgent('', portraitSeed(tool), CIVILIAN_PORTRAIT_IDS)
}

function sessionProfessionalName(): string {
  const active = agentContext.activeAgent
  if (!active?.code?.trim()) return ''
  const direct = active.name?.trim()
  if (direct && !looksLikeTechnicalId(direct) && !isDigitalOfficerDisplayName(direct)) return direct
  const resolved = agentContext.nameForSession({
    agentCode: active.code,
    agentName: active.name,
    hermesProfile: active.hermesProfile,
  }).trim()
  if (resolved && !looksLikeTechnicalId(resolved) && !isDigitalOfficerDisplayName(resolved)) return resolved
  return ''
}

function mateName(tool: ToolCallInfo): string {
  const agent = registeredAgent(tool)
  return resolveSubagentMateName({
    tool,
    registryName: agent?.name,
    sessionAgentName: sessionProfessionalName(),
    catalogLabel: bootstrap.toolLabel(tool.toolName),
  })
}

function allTools(): ToolCallInfo[] {
  return props.allTools ?? []
}

function childTools(tool: ToolCallInfo): ToolCallInfo[] {
  return descendantsOf(tool, props.allTools)
}

function isCardSelected(tool: ToolCallInfo): boolean {
  const key = toolKey(tool)
  if (props.activeToolKey && props.activeToolKey === key) return true
  if (!props.activeToolKey) return false
  return childTools(tool).some((c, i) => (c.toolCallId || childKey(c, i)) === props.activeToolKey)
}

function isExpanded(tool: ToolCallInfo): boolean {
  const key = toolKey(tool)
  if (Object.prototype.hasOwnProperty.call(expanded, key)) return expanded[key]
  return isToolRunning(tool) || isCardSelected(tool)
}

function toggleExpand(tool: ToolCallInfo) {
  const key = toolKey(tool)
  expanded[key] = !isExpanded(tool)
}

watch(
  () => props.activeToolKey,
  (key) => {
    if (!key) return
    for (const tool of props.tools) {
      if (isCardSelected(tool)) expanded[toolKey(tool)] = true
    }
  },
  { immediate: true },
)

function formatToolName(tool: ToolCallInfo): string {
  const fromServer = tool.displayName?.trim()
  if (fromServer) return fromServer
  return bootstrap.toolLabel(tool.toolName)
}

function toolIconKind(tool: ToolCallInfo): string {
  return resolveToolIconKind(tool)
}

function isToolRunning(tool: ToolCallInfo): boolean {
  const s = effectiveToolStatus(tool, allTools())
  return s === 'running' || s === 'awaiting'
}

function isToolAwaiting(tool: ToolCallInfo): boolean {
  return effectiveToolStatus(tool, allTools()) === 'awaiting'
}

function isToolError(tool: ToolCallInfo): boolean {
  return effectiveToolStatus(tool, allTools()) === 'error'
}

function toolStatusClass(tool: ToolCallInfo): 'running' | 'completed' | 'error' {
  return effectiveStatusClass(tool, allTools())
}

function toolStatusLabel(tool: ToolCallInfo): string {
  return effectiveStatusLabel(tool, allTools())
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
  return [formatToolName(tool), toolStatusLabel(tool), toolDescription(tool), formatToolDuration(tool), '点击查看执行详情']
    .filter(Boolean)
    .join('\n')
}

function isToolActive(key: string): boolean {
  return !!props.activeToolKey && props.activeToolKey === key
}

function onOpen(toolKeyValue: string, tool: ToolCallInfo) {
  expanded[toolKey(tool)] = true
  emit('open-tool', {
    messageId: props.messageId,
    toolKey: toolKeyValue,
    tool,
  })
}
</script>

<style scoped lang="scss">
.subagent-crew {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-width: 0;
}

.crew-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  overflow: visible;
  padding: 8px 4px 6px;
}

.crew-faces {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  overflow: visible;
  padding: 4px 3px;
}

.crew-face {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-left: -8px;
  border-radius: 50%;
  overflow: visible;
  box-shadow: 0 0 0 2px #fff;

  &:first-child {
    margin-left: 0;
  }

  &.running {
    box-shadow: 0 0 0 2px #fff, 0 0 0 3px rgba(79, 70, 229, 0.45);
    animation: mate-bob 1.6s ease-in-out infinite;
  }
}

@keyframes mate-bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

@media (prefers-reduced-motion: reduce) {
  .crew-face.running {
    animation: none;
  }
}

.crew-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.crew-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

.crew-meta {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.mate-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.22));
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(248, 250, 252, 0.7));
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);

  &.running {
    border-color: rgba(99, 102, 241, 0.28);
    background: linear-gradient(180deg, rgba(238, 242, 255, 0.9), rgba(255, 255, 255, 0.78));
  }

  &.completed {
    border-color: rgba(16, 185, 129, 0.18);
  }

  &.error {
    border-color: rgba(239, 68, 68, 0.22);
  }

  &.active {
    box-shadow: 0 0 0 1px rgba(56, 102, 245, 0.18);
  }
}

.mate-head {
  position: relative;
  z-index: 1;
}

.mate-toggle {
  display: flex;
  align-items: stretch;
  width: 100%;
  min-width: 0;
}

.mate-open {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1 1 auto;
  min-width: 0;
  padding: 10px 4px 10px 12px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;

  &.active {
    background: rgba(56, 102, 245, 0.06);
  }
}

.mate-chevron-btn {
  flex-shrink: 0;
  padding: 10px 12px;
  border: 0;
  background: transparent;
  cursor: pointer;
  color: inherit;
}

.mate-avatar {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 50%;

  &.running {
    box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.45);
  }
}

.mate-identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1 1 auto;
}

.mate-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.mate-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

.mate-task {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.mate-aside {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.mate-time,
.mate-chevron {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.mate-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-muted);

  &.running { color: #4f46e5; }
  &.completed { color: #059669; }
  &.error { color: #dc2626; }
}

.status-glyph {
  display: inline-flex;
  width: 10px;
  height: 10px;
}

.status-spin {
  width: 8px;
  height: 8px;
  border: 1.5px solid rgba(79, 70, 229, 0.25);
  border-top-color: #4f46e5;
  border-radius: 50%;
  animation: mate-spin 0.7s linear infinite;
}

.status-done,
.status-error {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.mate-body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 8px 8px;
}

.mate-empty {
  margin: 0 4px 6px;
  padding: 8px 10px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.mate-tool {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  padding: 6px 8px;
  border: 0;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.55);
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;

  &:hover,
  &.active {
    background: rgba(56, 102, 245, 0.08);
  }
}

.mate-tool-name {
  flex: 0 1 auto;
  max-width: 28%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.mate-tool-desc {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.mate-tool-status,
.mate-tool-time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-muted);
}

.mate-tool-status.running { color: #4f46e5; }
.mate-tool-status.completed { color: #059669; }
.mate-tool-status.error { color: #dc2626; }

.mate-open-detail {
  align-self: flex-end;
  margin: 4px 4px 0;
  padding: 4px 8px;
  border: 0;
  background: transparent;
  color: #4f46e5;
  font-size: var(--font-size-xs);
  cursor: pointer;
}

@keyframes mate-spin {
  to { transform: rotate(360deg); }
}
</style>
