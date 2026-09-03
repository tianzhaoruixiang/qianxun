<template>
  <a-drawer
    :open="open"
    placement="right"
    :width="drawerWidth"
    :destroy-on-close="false"
    :mask="true"
    :mask-closable="false"
    :keyboard="true"
    root-class-name="tool-execution-drawer"
    @update:open="onOpenChange"
  >
    <template #title>
      <div v-if="tool" class="drawer-title">
        <ToolCallIcon :kind="iconKind" :status="statusClass" />
        <div class="drawer-title-text">
          <div class="drawer-title-name">{{ displayName }}</div>
          <div class="drawer-title-meta">
            <span class="drawer-status" :class="statusClass">{{ statusLabel }}</span>
            <span class="drawer-dot" aria-hidden="true">·</span>
            <span class="drawer-time">{{ durationLabel }}</span>
          </div>
        </div>
      </div>
      <span v-else>工具执行详情</span>
    </template>

    <div v-if="tool" class="drawer-body">
      <p v-if="summaryLine" class="drawer-summary">{{ summaryLine }}</p>
      <ToolDetailBody :tool="tool" />
    </div>
    <a-empty v-else description="未选中工具调用" />
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { ToolCallInfo } from '@/types/chat'
import ToolCallIcon from '@/components/ToolCallIcon.vue'
import ToolDetailBody from '@/components/ToolDetailBody.vue'
import { resolveToolIconKind } from '@/utils/toolIcon'
import { toolCallFailure, toolCallSummaryLine } from '@/utils/toolDetailAdapt'
import { useBootstrapStore } from '@/stores/useBootstrapStore'

const props = defineProps<{
  open: boolean
  tool: ToolCallInfo | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const bootstrap = useBootstrapStore()
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1200)

function onResize() {
  viewportWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})

const drawerWidth = computed(() => {
  const w = viewportWidth.value
  if (w <= 640) return '100%'
  if (w <= 960) return Math.min(440, Math.floor(w * 0.88))
  return 520
})

function onOpenChange(v: boolean) {
  emit('update:open', v)
}

const displayName = computed(() => {
  if (!props.tool) return '工具执行详情'
  const fromServer = props.tool.displayName?.trim()
  if (fromServer) return fromServer
  return bootstrap.toolLabel(props.tool.toolName)
})

const iconKind = computed(() => (props.tool ? resolveToolIconKind(props.tool) : 'tool'))

function statusOf(tool: ToolCallInfo): string {
  return (tool.status || 'running').toLowerCase()
}

function isRunning(tool: ToolCallInfo): boolean {
  const s = statusOf(tool)
  return s === 'started' || s === 'running' || s === 'awaiting' || s === 'background'
}

function isAwaiting(tool: ToolCallInfo): boolean {
  const s = statusOf(tool)
  return s === 'awaiting' || s === 'background'
}

function isError(tool: ToolCallInfo): boolean {
  return !!toolCallFailure(tool)
}

const statusClass = computed<'running' | 'completed' | 'error'>(() => {
  if (!props.tool) return 'completed'
  if (isError(props.tool)) return 'error'
  if (isRunning(props.tool)) return 'running'
  return 'completed'
})

const statusLabel = computed(() => {
  if (!props.tool) return ''
  if (isError(props.tool)) return '失败'
  if (isAwaiting(props.tool)) return '后台执行中'
  if (isRunning(props.tool)) return '进行中'
  return '成功'
})

function formatDurationMs(ms: number): string {
  if (ms < 1000) return `${Math.max(0, Math.round(ms))}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const durationLabel = computed(() => {
  if (!props.tool) return '—'
  if (isAwaiting(props.tool)) return '等待汇入…'
  if (isRunning(props.tool)) return '执行中…'
  const tool = props.tool
  const dur = tool.durationMs
    ?? (tool.durationSeconds != null ? Math.round(tool.durationSeconds * 1000) : undefined)
    ?? (tool.startedAt && tool.endedAt ? Math.max(0, tool.endedAt - tool.startedAt) : undefined)
  if (dur == null) return '—'
  return formatDurationMs(dur)
})

const summaryLine = computed(() => {
  if (!props.tool) return ''
  return toolCallSummaryLine(props.tool, 200)
})
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';

.drawer-title {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  padding-right: 8px;
}

.drawer-title-text {
  min-width: 0;
  flex: 1;
}

.drawer-title-name {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--text-primary);
  word-break: break-word;
}

.drawer-title-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  line-height: 1.4;
}

.drawer-status {
  font-weight: 600;

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

.drawer-dot {
  opacity: 0.5;
}

.drawer-time {
  font-variant-numeric: tabular-nums;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.drawer-summary {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(56, 102, 245, 0.06), rgba(54, 169, 255, 0.04));
  border: 1px solid rgba(56, 102, 245, 0.12);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.5;
  word-break: break-word;
}
</style>

<style lang="scss">
.tool-execution-drawer {
  /* 遮罩不拦截点击：可点消息里的另一个工具，直接切换详情而不是先关掉抽屉 */
  pointer-events: none;

  .ant-drawer-mask {
    background: transparent;
    pointer-events: none;
  }

  .ant-drawer-content-wrapper {
    pointer-events: auto;
    box-shadow:
      -8px 0 28px rgba(15, 23, 42, 0.08),
      -1px 0 0 rgba(148, 163, 184, 0.18);
  }

  .ant-drawer-header {
    padding: 16px 20px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  }

  .ant-drawer-header-title {
    align-items: flex-start;
  }

  .ant-drawer-header-title .ant-drawer-title {
    flex: 1;
    min-width: 0;
    margin-right: 8px;
    font-size: 14px;
    font-weight: 600;
    line-height: 1.35;
  }

  .ant-drawer-body {
    padding: 16px 20px 24px;
    background: linear-gradient(180deg, #f8fafc 0%, #ffffff 120px);
  }

  .ant-drawer-content {
    background: #fff;
  }
}
</style>
