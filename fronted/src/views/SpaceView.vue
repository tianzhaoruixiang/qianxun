<template>
  <div class="space-view">
    <header class="space-header">
      <div class="greeting">
        <h1>{{ greetingTitle }}</h1>
        <p class="sub">{{ greetingSub }}</p>
      </div>
      <a-button type="primary" size="large" class="cta-chat" @click="goNewChat">
        <template #icon><AppGlyph name="chat" size="sm" /></template>
        新建对话
      </a-button>
    </header>

    <section class="quick-grid" aria-label="快捷入口">
      <router-link
        v-for="item in quickLinks"
        :key="item.to"
        :to="item.to"
        class="quick-card"
      >
        <span class="quick-icon">
          <AppGlyph :name="item.icon" size="lg" />
        </span>
        <span class="quick-title">{{ item.title }}</span>
        <span class="quick-desc">{{ item.desc }}</span>
      </router-link>
    </section>

    <section class="panel obs-panel-wrap">
      <ObservabilityPanel />
    </section>

    <section class="panel recent-panel">
      <div class="panel-head">
        <h2>最近会话</h2>
        <router-link to="/chat" class="link-more">{{ brandCopy.spaceRecentMore }}</router-link>
      </div>
      <a-select
        v-model:value="chatStore.historyAgentFilter"
        class="space-agent-filter"
        :options="chatStore.historyAgentOptions"
        placeholder="按专业智能体筛选"
      />
      <a-spin :spinning="sessionsLoading">
        <template v-if="groupedRecent.length">
          <div v-for="group in groupedRecent" :key="group.key" class="space-group">
            <h3 class="space-group-title">{{ group.label }}</h3>
            <ul class="session-list">
              <li v-for="s in group.sessions" :key="s.id" class="session-row">
                <button type="button" class="session-main" @click="openSession(s.id)">
                  <span class="session-title">{{ s.title }}</span>
                  <span class="session-preview">{{ s.lastMessage || '暂无预览' }}</span>
                </button>
                <span class="session-meta">
                  <span class="agent-chip">{{ s.agentName }}</span>
                  {{ formatTime(s.updatedAt) }} · {{ s.messageCount }} 条
                </span>
              </li>
            </ul>
          </div>
        </template>
        <a-empty v-else :description="brandCopy.spaceEmpty">
          <template #image>
            <AppGlyph name="chat" size="xl" class="empty-glyph" />
          </template>
        </a-empty>
      </a-spin>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppGlyph from '@/components/AppGlyph.vue'
import ObservabilityPanel from '@/components/ObservabilityPanel.vue'
import { useChatStore } from '@/stores/useChatStore'
import { useUserProfileStore } from '@/stores/useUserProfileStore'
import { agentGroupKey, DIGITAL_OFFICER_KEY } from '@/utils/agentDisplay'
import { brandCopy } from '@/utils/brandCopy'
import { getSystemName } from '@/utils/systemName'

const router = useRouter()
const chatStore = useChatStore()
const userProfile = useUserProfileStore()

const sessionsLoading = ref(false)

const quickLinks = computed(() => {
  return [
    { to: '/chat', title: brandCopy.spaceQuickChatTitle, desc: brandCopy.spaceQuickChatDesc, icon: 'chat' },
    {
      to: '/market',
      title: brandCopy.spaceQuickMarketTitle,
      desc: brandCopy.spaceQuickMarketDesc,
      icon: 'market',
    },
  ]
})

const greetingTitle = computed(() => {
  const hour = new Date().getHours()
  let part = '您好'
  if (hour < 12) part = '早上好'
  else if (hour < 18) part = '下午好'
  else part = '晚上好'
  return `${part}，${userProfile.displayLabel}`
})

const greetingSub = computed(() => brandCopy.spaceGreetingFallback)

const recentSessions = computed(() => {
  const list = [...chatStore.filteredHistorySessions]
  list.sort((a, b) => (b.updatedAt || b.createdAt) - (a.updatedAt || a.createdAt))
  return list.slice(0, 12)
})

const groupedRecent = computed(() => {
  const officerName = getSystemName()
  const groups: Array<{ key: string; label: string; sessions: typeof recentSessions.value }> = []
  const index = new Map<string, number>()
  for (const s of recentSessions.value) {
    const key = agentGroupKey(s)
    const label = key === DIGITAL_OFFICER_KEY ? officerName : (s.agentName || officerName)
    let i = index.get(key)
    if (i == null) {
      i = groups.length
      index.set(key, i)
      groups.push({ key, label, sessions: [] })
    }
    groups[i].sessions.push(s)
  }
  return groups
})

function formatTime(ms: number): string {
  if (!ms) return '—'
  try {
    return new Date(ms).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '—'
  }
}

async function goNewChat() {
  chatStore.newConversation()
  await router.push('/chat')
}

async function openSession(sessionId: string) {
  await router.push(`/chat/${encodeURIComponent(sessionId)}`)
}

onMounted(async () => {
  await userProfile.ensureLoaded()
  sessionsLoading.value = true
  try {
    await chatStore.refreshHistoryFromServer()
  } finally {
    sessionsLoading.value = false
  }
})
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';

.space-view {
  height: 100%;
  overflow: auto;
  padding: 24px 28px 40px;
  background: var(--bg-base);
}

.space-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;

  .greeting h1 {
    margin: 0 0 8px;
    font-size: var(--font-size-xxl);
    font-weight: var(--font-weight-semibold);
    color: var(--text-primary);
  }

  .sub {
    margin: 0;
    font-size: var(--font-size-md);
    color: var(--text-muted);
    max-width: 520px;
    line-height: var(--line-height-normal);
  }

  .cta-chat {
    flex-shrink: 0;
    display: inline-flex !important;
    align-items: center;
    gap: 6px;
    line-height: 1.25;

    :deep(.ag-icon) {
      flex-shrink: 0;
    }
  }
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 24px;
}

.quick-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px 18px;
  background: var(--card-bg-solid, var(--card-bg));
  border: 1px solid var(--card-border, var(--border-subtle));
  border-radius: var(--radius-md);
  box-shadow: var(--card-shadow);
  text-decoration: none;
  color: inherit;
  transition:
    box-shadow 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.28s ease;

  &:hover {
    border-color: var(--card-border-hover, var(--border-accent));
    box-shadow: var(--card-shadow-hover);
    transform: translateY(-2px);
  }

  &:active {
    transform: translateY(0);
    box-shadow: var(--card-shadow-pressed, var(--shadow-sm));
  }

  .quick-icon {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 0;
    background: rgba(118, 118, 128, 0.1);
    color: var(--color-primary-dark);
  }

  .quick-title {
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    letter-spacing: var(--letter-spacing-tight);
    color: var(--text-primary);
  }

  .quick-desc {
    font-size: var(--font-size-xs);
    color: var(--text-muted);
    line-height: var(--line-height-tight);
  }
}

.panel {
  background: var(--card-bg-solid, var(--card-bg));
  border: 1px solid var(--card-border, var(--border-subtle));
  border-radius: var(--radius-md);
  padding: 20px 22px;
  box-shadow: var(--card-shadow);

  h2 {
    margin: 0 0 14px;
    font-size: var(--font-size-lg);
    font-weight: var(--font-weight-semibold);
    letter-spacing: var(--letter-spacing-tight);
    color: var(--text-primary);
  }
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  h2 {
    margin: 0;
  }

  .link-more {
    font-size: var(--font-size-sm);
    color: var(--color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.space-agent-filter {
  width: 100%;
  max-width: 280px;
  margin-bottom: 12px;
}

.space-group-title {
  margin: 8px 0 4px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-muted);
}

.agent-chip {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  border-radius: 4px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  font-size: var(--font-size-xs);
}

.recent-panel .session-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.session-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px 12px;
  align-items: center;
  padding: 12px 10px;
  margin: 0 -10px;
  border-radius: var(--radius-sm);
  border-bottom: 1px solid var(--border-subtle);
  transition: background 0.15s ease;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: rgba(59, 130, 246, 0.06);
  }
}

.session-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  text-align: left;
  min-width: 0;

  &:hover .session-title {
    color: var(--color-primary);
  }
}

.session-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
}

.session-agent {
  font-size: var(--font-size-xs);
  color: var(--color-primary-dark);
}

.session-preview {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: var(--line-height-tight);
}

.session-meta {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  white-space: nowrap;
}

.empty-glyph {
  width: 42px;
  height: 42px;
}
</style>
