<template>
  <div class="history-popup" :class="{ 'history-popup--drawer': layout === 'drawer', 'history-popup--sidenav': layout === 'sidenav' }">
    <div v-if="layout !== 'sidenav'" class="search-area">
      <a-input v-model:value="chatStore.searchKeyword" placeholder="搜索标题或专业智能体" allow-clear class="search-input">
        <template #prefix>
          <AppGlyph name="search" size="sm" class="search-icon" />
        </template>
      </a-input>
      <a-select
        v-model:value="chatStore.historyAgentFilter"
        class="agent-filter"
        :options="chatStore.historyAgentOptions"
        placeholder="按专业智能体筛选"
      />
    </div>

    <div
      ref="listEl"
      class="session-list"
    >
        <template v-if="chatStore.filteredHistorySessions.length > 0">
          <div v-for="group in chatStore.groupedHistorySessions" :key="group.key" class="session-group">
            <button type="button" class="session-group-title" @click="toggleGroup(group.key)">
              <AgentAvatar :group-key="group.key" :label="group.label" size="md" />
              <span class="session-group-name">{{ group.label }}</span>
              <span class="session-group-count">{{ group.sessions.length }}</span>
              <span class="session-group-chevron" :class="{ open: isGroupOpen(group.key) }" />
            </button>
            <template v-if="isGroupOpen(group.key)">
              <div
                v-for="session in sortedSessions(group.sessions)"
                :key="session.id"
                class="session-item"
                :class="{
                  active: session.id === chatStore.conversationId,
                  streaming: isStreamingSession(session.id),
                }"
                @click="handleSelect(session)"
              >
                <div class="session-body">
                  <div class="session-title-row">
                    <span
                      v-if="isStreamingSession(session.id)"
                      class="session-stream-spinner"
                      aria-hidden="true"
                    />
                    <span class="session-title">{{ session.title }}</span>
                    <span class="session-time">{{ formatSessionTime(session.updatedAt) }}</span>
                  </div>
                  <p v-if="layout !== 'sidenav'" class="session-agent-tag">{{ session.agentName }}</p>
                  <p v-if="session.lastMessage" class="session-preview">{{ session.lastMessage }}</p>
                  <p v-else class="session-preview muted">暂无消息</p>
                </div>

                <a-dropdown :trigger="['click']" placement="bottomRight" @click.stop>
                  <template #overlay>
                    <a-menu class="session-more-menu" @click="(e) => onMenuClick(e, session)">
                      <a-menu-item key="rename">重命名</a-menu-item>
                      <a-menu-item key="delete" danger>删除</a-menu-item>
                    </a-menu>
                  </template>
                  <button type="button" class="more-btn" @click.stop>
                    <AppGlyph name="more" size="sm" />
                  </button>
                </a-dropdown>
              </div>
            </template>
          </div>
        </template>

        <div v-else-if="chatStore.historyLoadError && chatStore.historySessions.length === 0" class="empty-state">
          <AppGlyph name="empty" size="lg" class="empty-icon" />
          <span>{{ chatStore.historyLoadError }}</span>
          <button type="button" class="retry-btn" @click="chatStore.retryHistoryLoad()">重试</button>
        </div>

        <div v-else-if="chatStore.searchKeyword.trim() || chatStore.historyAgentFilter" class="empty-state">
          <AppGlyph name="search" size="lg" class="empty-icon" />
          <span>未找到匹配会话</span>
        </div>

        <div v-else class="empty-state">
          <AppGlyph name="empty" size="lg" class="empty-icon" />
          <span>暂无历史会话</span>
        </div>

        <div v-if="chatStore.historySessions.length > 0" class="list-footer">
          <button
            v-if="chatStore.historyLoadError"
            type="button"
            class="retry-btn"
            @click="chatStore.retryHistoryLoad()"
          >
            加载失败，点击重试
          </button>
          <button
            v-else-if="chatStore.historyHasMore && !chatStore.historyLoadingMore"
            type="button"
            class="load-more-btn"
            @click="handleLoadMore"
          >
            加载更多
          </button>
          <span v-else-if="chatStore.historyLoadingMore" class="list-status">正在加载更多…</span>
          <span v-else class="list-status">没有更多会话</span>
        </div>
      </div>

    <div
      v-if="chatStore.historyLoading && chatStore.historySessions.length === 0"
      class="list-spin-mask"
    >
      <a-spin />
    </div>

    <a-modal
      v-model:open="renameOpen"
      title="重命名会话"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="renameSubmitting"
      :z-index="1250"
      destroy-on-close
      @ok="handleRenameOk"
    >
      <a-input v-model:value="renameValue" placeholder="会话标题" maxlength="200" show-count @pressEnter="handleRenameOk" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { useChatStore } from '@/stores/useChatStore'
import type { HistorySession } from '@/types/chat'
import AppGlyph from '@/components/AppGlyph.vue'
import AgentAvatar from './AgentAvatar.vue'

withDefaults(
  defineProps<{
    /** sidenav：侧栏主入口；drawer / popover 为兼容旧布局 */
    layout?: 'drawer' | 'popover' | 'sidenav'
  }>(),
  { layout: 'drawer' },
)

const emit = defineEmits<{
  (e: 'select', session: HistorySession): void
}>()

const chatStore = useChatStore()

const listEl = ref<HTMLElement | null>(null)

function handleLoadMore() {
  void chatStore.loadMoreHistory()
}

const renameOpen = ref(false)
const renameSubmitting = ref(false)
const renameTarget = ref<HistorySession | null>(null)
const renameValue = ref('')

const RECENT_MS = 14 * 24 * 60 * 60 * 1000
const userCollapsed = ref(new Set<string>())
const userExpanded = ref(new Set<string>())

const defaultOpenKeys = computed(() => {
  const groups = chatStore.groupedHistorySessions
  const open = new Set<string>()
  const now = Date.now()
  let newestKey = ''
  let newestAt = 0
  for (const g of groups) {
    const latest = g.sessions.reduce((m, s) => Math.max(m, s.updatedAt || s.createdAt || 0), 0)
    if (latest > newestAt) {
      newestAt = latest
      newestKey = g.key
    }
    const hasCurrent = g.sessions.some((s) => s.id === chatStore.conversationId)
    if (hasCurrent || latest >= now - RECENT_MS) open.add(g.key)
  }
  if (open.size === 0 && newestKey) open.add(newestKey)
  return open
})

function isGroupOpen(key: string): boolean {
  if (userCollapsed.value.has(key)) return false
  if (userExpanded.value.has(key)) return true
  return defaultOpenKeys.value.has(key)
}

function toggleGroup(key: string) {
  const nextCollapsed = new Set(userCollapsed.value)
  const nextExpanded = new Set(userExpanded.value)
  if (isGroupOpen(key)) {
    nextCollapsed.add(key)
    nextExpanded.delete(key)
  } else {
    nextExpanded.add(key)
    nextCollapsed.delete(key)
  }
  userCollapsed.value = nextCollapsed
  userExpanded.value = nextExpanded
}

function sortedSessions(sessions: HistorySession[]): HistorySession[] {
  return [...sessions].sort((a, b) => (b.updatedAt || b.createdAt) - (a.updatedAt || a.createdAt))
}

function isStreamingSession(id: string): boolean {
  return Boolean(id) && chatStore.streamingSessionIds.has(id)
}

watch(
  () => chatStore.conversationId,
  async (id) => {
    if (!id) return
    const g = chatStore.groupedHistorySessions.find((x) => x.sessions.some((s) => s.id === id))
    if (!g) return
    const el = listEl.value
    const prevScroll = el?.scrollTop ?? 0
    const nextCollapsed = new Set(userCollapsed.value)
    const nextExpanded = new Set(userExpanded.value)
    nextCollapsed.delete(g.key)
    nextExpanded.add(g.key)
    userCollapsed.value = nextCollapsed
    userExpanded.value = nextExpanded
    // 展开其他 profile 分组时保持侧栏滚动，避免整页跟着跳
    await nextTick()
    if (el) el.scrollTop = prevScroll
  },
)

function formatSessionTime(ts: number): string {
  const d = new Date(ts)
  if (!Number.isFinite(d.getTime())) return ''
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
  if (sameDay) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  const y = d.getFullYear()
  const ny = now.getFullYear()
  if (y === ny) {
    return `${d.getMonth() + 1}/${d.getDate()}`
  }
  return `${y}/${d.getMonth() + 1}/${d.getDate()}`
}

async function handleSelect(session: HistorySession) {
  await chatStore.switchSession(session.id)
  emit('select', session)
}

function onMenuClick(e: { key: string | number }, session: HistorySession) {
  const key = String(e.key)
  if (key === 'rename') {
    renameTarget.value = session
    renameValue.value = session.title
    renameOpen.value = true
    return
  }
  if (key === 'delete') {
    Modal.confirm({
      title: '删除会话',
      content: `确定删除「${session.title}」？该会话下所有消息将一并删除。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      zIndex: 1250,
      async onOk() {
        await chatStore.deleteSession(session.id)
      },
    })
  }
}

async function handleRenameOk() {
  const target = renameTarget.value
  if (!target) return
  const t = renameValue.value.trim()
  if (!t) {
    message.warning('标题不能为空')
    return Promise.reject(new Error('validation'))
  }
  renameSubmitting.value = true
  try {
    await chatStore.renameSession(target.id, t)
    renameOpen.value = false
    renameTarget.value = null
  } finally {
    renameSubmitting.value = false
  }
}
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';
.history-popup {
  position: relative;
  max-height: 440px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  animation: popup-in 200ms ease-out both;

  @keyframes popup-in {
    from {
      opacity: 0;
      transform: translateY(4px) scale(0.97);
    }

    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  .search-area {
    flex-shrink: 0;
    padding: 0 14px 8px;
    border-bottom: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.15));

    .search-input {
      border-radius: 8px;

      :deep(.ant-input) {
        font-size: var(--font-size-sm);
        background: var(--input-bg, #f8fafc);
        color: var(--text-primary, #1e293b);

        &::placeholder {
          color: var(--text-muted, #94a3b8);
        }
      }

      .search-icon {
        color: var(--text-muted, #94a3b8);
        font-size: var(--font-size-sm);
      }
    }

    .agent-filter {
      width: 100%;
      margin-top: 8px;
    }
  }

  .list-spin-mask {
    position: absolute;
    inset: 0;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.45);
    pointer-events: none;
  }

  .session-list {
    flex: 1 1 0;
    min-height: 0;
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
    padding: 6px 0;
    max-height: 320px;
    scrollbar-width: thin;
    scrollbar-color: var(--border-subtle, rgba(148, 163, 184, 0.3)) transparent;

    &::-webkit-scrollbar {
      width: 5px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background-color: var(--border-subtle, rgba(148, 163, 184, 0.3));
      border-radius: 3px;
    }

    .session-group {
      padding: 2px 0 6px;
    }

    .session-group-title {
      display: flex;
      align-items: center;
      gap: 8px;
      width: calc(100% - 8px);
      margin: 4px 4px 2px;
      padding: 4px 6px;
      border: none;
      background: transparent;
      cursor: pointer;
      text-align: left;
      border-radius: 8px;
      line-height: 1.25;
      min-height: 36px;
      box-sizing: border-box;

      &:hover {
        background: rgba(59, 130, 246, 0.06);
      }
    }

    .session-group-name {
      flex: 1;
      min-width: 0;
      font-size: var(--font-size-xs);
      line-height: 1.25;
      font-weight: var(--font-weight-medium);
      color: var(--text-secondary);
      letter-spacing: 0.02em;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .session-group-count {
      flex-shrink: 0;
      font-size: var(--font-size-xs);
      color: var(--text-muted);
    }

    .session-group-chevron {
      width: 0;
      height: 0;
      flex-shrink: 0;
      border-style: solid;
      border-width: 4px 0 4px 6px;
      border-color: transparent transparent transparent #94a3b8;
      opacity: 0.75;
      transition: transform 0.15s ease;

      &.open {
        transform: rotate(90deg);
      }
    }

    .session-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 10px;
      cursor: pointer;
      transition: background 0.15s ease, box-shadow 0.15s ease;
      margin: 2px 6px;
      border-radius: 10px;
      content-visibility: auto;
      contain-intrinsic-size: auto 56px;

      &:hover {
        background: rgba(59, 130, 246, 0.08);

        .more-btn {
          opacity: 1;
        }
      }

      &.active {
        background: #e5ebf7;
        box-shadow: var(--shadow-sm, 0 1px 2px rgba(0, 0, 0, 0.05));

        .session-title {
          font-weight: var(--font-weight-medium);
          color: var(--text-primary);
        }
      }

      .session-body {
        flex: 1;
        min-width: 0;
      }

      .session-title-row {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
      }

      .session-stream-spinner {
        flex-shrink: 0;
        width: var(--icon-size-xs);
        height: var(--icon-size-xs);
        border-radius: 50%;
        background: conic-gradient(
          from 180deg,
          rgba(56, 102, 245, 0) 0deg,
          rgba(54, 169, 255, 0.15) 80deg,
          #36a9ff 200deg,
          #3866f5 300deg,
          rgba(56, 102, 245, 0) 360deg
        );
        -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 1.9px));
        mask: radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 1.9px));
        animation: session-stream-spin 0.75s linear infinite;
      }

      .session-title {
        flex: 1;
        min-width: 0;
        font-size: var(--font-size-xs);
        font-weight: var(--font-weight-regular);
        color: var(--text-secondary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        line-height: var(--line-height-tight);
      }

      .session-time {
        flex-shrink: 0;
        font-size: var(--font-size-xs);
        color: var(--text-muted, #94a3b8);
      }

      .session-agent-tag {
        margin: 4px 0 0;
        display: inline-flex;
        max-width: 100%;
        padding: 1px 8px;
        border-radius: 999px;
        font-size: var(--font-size-xs);
        line-height: var(--line-height-normal);
        color: var(--color-primary-dark, #2563eb);
        background: rgba(59, 130, 246, 0.1);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .session-preview {
        margin: 4px 0 0;
        font-size: var(--font-size-xs);
        line-height: var(--line-height-tight);
        color: var(--text-muted, #64748b);
        display: -webkit-box;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;

        &.muted {
          opacity: 0.65;
        }
      }

      .more-btn {
        @include icon-btn(var(--icon-btn-size-sm));
        margin-top: 0;
        border: none;
        background: transparent;
        border-radius: 6px;
        cursor: pointer;
        color: var(--text-muted, #94a3b8);
        opacity: 0;
        transition: opacity 0.15s ease, background 0.15s ease;
        flex-shrink: 0;

        &:hover {
          background: var(--bg-base, #f0f5ff);
          color: var(--color-primary, #0ea5e9);
        }
      }
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px 12px;
      gap: 10px;
      color: var(--text-muted, #94a3b8);
      font-size: var(--font-size-xs);

      .empty-icon {
        width: 32px;
        height: 32px;
      }

      .empty-hint {
        font-size: var(--font-size-xs);
        color: var(--text-muted);
      }
    }

    .list-footer {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      padding: 8px 10px 12px;
      color: var(--text-muted);
      font-size: var(--font-size-xs);
      text-align: center;
    }

    .list-hint,
    .list-status {
      margin: 0;
      line-height: var(--line-height-tight);
    }

    .retry-btn,
    .load-more-btn {
      border: none;
      background: transparent;
      color: #2563eb;
      cursor: pointer;
      font-size: var(--font-size-xs);
      padding: 4px 8px;
    }

    .load-more-btn {
      padding: 6px 14px;
      border-radius: 999px;
      background: rgba(59, 130, 246, 0.08);
      transition: background 0.15s ease;

      &:hover {
        background: rgba(59, 130, 246, 0.14);
      }
    }
  }

  &--drawer,
  &--sidenav {
    max-height: none;
    height: auto;
    min-height: 0;
    flex: 1 1 0;
    border-radius: 0;
    animation: none;

    .session-list {
      flex: 1 1 0;
      height: 0;
      max-height: none;
      min-height: 0;
      overflow-x: hidden;
      overflow-y: auto;
    }
  }

  &--sidenav {
    width: 100%;

    .session-list {
      padding: 0 0 4px;
      width: 100%;
      box-sizing: border-box;

      .session-group {
        padding: 2px 0 4px;
      }

      .session-group-title {
        width: 100%;
        margin: 2px 0;
        padding: 6px 12px;
        border-radius: 0;
      }

      .session-item {
        width: 100%;
        box-sizing: border-box;
        padding: 8px 12px;
        margin: 0;
        border-radius: 0;
        gap: 6px;

        .session-title-row {
          gap: 6px;
        }

        .session-title {
          flex: 1 1 auto;
        }

        .session-time {
          flex: 0 0 auto;
          max-width: 4.5em;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .session-preview {
          -webkit-line-clamp: 1;
          line-clamp: 1;
        }

        .more-btn {
          margin-right: 0;
        }

        &.active,
        &:hover {
          border-radius: 0;
        }
      }
    }
  }
}

.session-more-menu {
  min-width: 120px;
}

@keyframes session-stream-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
