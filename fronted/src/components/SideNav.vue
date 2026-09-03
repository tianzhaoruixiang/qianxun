<template>
  <aside class="side-nav" :style="{ width: layoutStore.sidebarWidth + 'px', backgroundImage: `url(${leftBg})` }">
    <div class="logo-area">
      <div class="logo-icon">
        <img :src="logoImg" alt="logo" />
      </div>
      <span class="logo-text">{{ systemName }}</span>
    </div>

    <div class="header-banner">
      <img class="header-image" :src="airobotImg" alt="AI Robot" />
    </div>

    <div class="search-box">
      <a-input v-model:value="chatStore.searchKeyword" placeholder="搜索历史会话" class="search-input" allow-clear>
        <template #prefix>
          <AppGlyph name="search" size="sm" />
        </template>
      </a-input>
    </div>

    <nav class="nav-menu">
      <router-link
        v-for="item in navItems"
        :key="item.key"
        :to="item.path"
        class="nav-item"
        :class="{ active: isNavItemActive(item) }"
        @click="onNavClick(item)"
      >
        <img :src="item.icon" class="nav-icon" />
        <span class="nav-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <section v-if="!layoutStore.sidebarCollapsed" class="history-area">
      <div class="history-head">历史会话</div>
      <HistorySessionPopup layout="sidenav" />
    </section>

    <div class="user-area">
      <a-dropdown :trigger="['click']" placement="topRight">
        <button type="button" class="user-trigger" aria-label="账号菜单">
          <a-avatar :size="24" class="user-avatar">
            <template #icon>
              <img :src="userImg" alt="" />
            </template>
          </a-avatar>
          <span class="user-name">{{ userProfile.displayLabel }}</span>
        </button>
        <template #overlay>
          <a-menu class="user-menu" @click="onUserMenu">
            <a-menu-item v-if="userProfile.isAdmin" key="create-user">创建用户</a-menu-item>
            <a-menu-item v-if="userProfile.isAdmin" key="system-settings">系统设置</a-menu-item>
            <a-menu-item key="logout">退出登录</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
    <CreateUserModal v-model:open="createUserOpen" />
    <SystemSettingsModal v-model:open="systemSettingsOpen" />
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLayoutStore } from '@/stores/useLayoutStore'
import { useUserProfileStore } from '@/stores/useUserProfileStore'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import { useChatStore } from '@/stores/useChatStore'
import AppGlyph from '@/components/AppGlyph.vue'
import HistorySessionPopup from './HistorySessionPopup.vue'
import CreateUserModal from './CreateUserModal.vue'
import SystemSettingsModal from './SystemSettingsModal.vue'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import leftBg from '@/assets/images/leftBg.png'
import logoImg from '@/assets/images/logo.png'
import airobotImg from '@/assets/images/airobot.png'
import userImg from '@/assets/images/portraits/user-officer.webp'
import menuSpace from '@/assets/images/menu_space.png'
import menuQianxun from '@/assets/images/menu_qianxun.png'
import menuSupermarket from '@/assets/images/menu_supermarket.png'

type NavItem = { key: string; label: string; path: string; icon: string }

const layoutStore = useLayoutStore()
const userProfile = useUserProfileStore()
const agentContext = useAgentContextStore()
const hermesProfileStore = useHermesProfileStore()
const chatStore = useChatStore()
const bootstrap = useBootstrapStore()
const route = useRoute()
const router = useRouter()
const createUserOpen = ref(false)
const systemSettingsOpen = ref(false)
const systemName = computed(() => bootstrap.systemName)

/** 当前为专业智能体对话（市场进入或会话绑定），侧栏高亮「智能体」而非数智干警 */
const isSupermarketAgentChat = computed(() => {
  if (route.name !== 'chat') return false
  const a = route.query.agent
  if (typeof a === 'string' && a.trim().length > 0) return true
  return !!agentContext.activeAgent?.code
})

function onNavClick(item: NavItem) {
  if (item.key !== 'qianxun') return
  agentContext.clearActiveAgent()
  hermesProfileStore.useDefaultProfile()
}

function isNavItemActive(item: NavItem) {
  if (item.key === 'qianxun') {
    return route.name === 'chat' && !isSupermarketAgentChat.value
  }
  if (item.key === 'market') {
    return route.path === '/market' || route.path.startsWith('/market/') || isSupermarketAgentChat.value
  }
  return route.path === item.path
}

function onUserMenu(info: { key: string | number }) {
  if (info.key === 'create-user') {
    createUserOpen.value = true
    return
  }
  if (info.key === 'system-settings') {
    systemSettingsOpen.value = true
    return
  }
  if (info.key === 'logout') {
    hermesProfileStore.resetToDefault()
    userProfile.logout()
    void router.replace({ name: 'login' })
  }
}

onMounted(() => {
  void userProfile.ensureLoaded()
  void bootstrap.ensureLoaded()
  void chatStore.refreshHistoryFromServer()
})

const navItems = computed<NavItem[]>(() => {
  const items: NavItem[] = [
    { key: 'space', label: '我的空间', path: '/', icon: menuSpace },
    { key: 'qianxun', label: systemName.value, path: '/chat', icon: menuQianxun },
    { key: 'market', label: '专业智能体', path: '/market', icon: menuSupermarket },
  ]
  return items
})
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';
@import '@/styles/mixins.scss';

.side-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background-color: transparent;
  background-repeat: no-repeat;
  background-position: center center;
  background-size: cover;
  border-right: 1px solid var(--border-subtle);
  padding: 16px 12px 0px;
  overflow: hidden;
  transform: translateZ(0);
  box-shadow: 1px 0 0 rgba(0, 0, 0, 0.04), 8px 0 24px rgba(0, 0, 0, 0.04);
  z-index: 9;
  position: relative;

  // 标题横幅区域
  .header-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    position: relative;
    flex-shrink: 0;

    .header-text {
      .header-title {
        font-family: var(--font-family-display);
        font-size: var(--font-size-xxl);
        font-weight: normal;
        line-height: var(--line-height-tight);
        letter-spacing: 0.06em;
        background: linear-gradient(64deg, #3861F4 -7%, #36ABFF 103%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
        text-fill-color: transparent;
        margin: 0 0 4px 0;
      }

      .header-subtitle {
        font-size: var(--font-size-xs);
        color: var(--text-primary);
        margin: 0;
        line-height: 28px;
        letter-spacing: 8px;
      }
    }

    .header-image {
      position: absolute;
      right: 4px;
      bottom: -14px;
      pointer-events: none;
    }
  }

  // Logo 区域
  .logo-area {
    position: relative;
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 30px;
    padding: 0 4px;
    flex-shrink: 0;

    .logo-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      img {
        max-width: 100%;
        max-height: 100%;
      }
    }

    .logo-text {
      font-family: var(--font-family-display);
      font-size: var(--font-size-display);
      color: #3861F4;
    }
  }

  // 搜索框
  .search-box {
    margin-bottom: 16px;
    flex-shrink: 0;

    :deep(.ant-input-affix-wrapper:focus),
    :deep(.ant-input-affix-wrapper-focused),
    :deep(.ant-input-affix-wrapper:hover) {
      border: 1px solid #EBEBEB !important;
      box-shadow: 0px 4px 6px 0px rgba(0, 0, 0, 0.07) !important;
    }

    .search-input {
      background: var(--input-bg);
      border: 1px solid #EBEBEB;
      border-radius: 34px;
      color: var(--text-primary);
      height: 35px;


      :deep(.ant-input) {
        background: transparent;
        color: var(--text-primary);

        &::placeholder {
          color: var(--text-muted);
        }
      }

      :deep(.ant-input-prefix) {
        color: var(--text-muted);
      }
    }
  }

  // 导航菜单
  .nav-menu {
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;

      .nav-item {
      display: flex;
      align-items: center;
      gap: 10px;
      height: 44px;
      padding: 0 12px;
      border-radius: var(--radius-sm);
      color: var(--text-primary);
      text-decoration: none;
      transition: all 0.2s ease;
      cursor: pointer;
      line-height: 1.25;
      box-sizing: border-box;

      &:hover {
        background: rgba(59, 130, 246, 0.08);
        color: var(--text-primary);
      }

      &.active {
        background: #fff;
        color: var(--color-primary-dark);
        box-shadow: 0px 4px 6px 3px rgba(0, 0, 0, 0.08);
      }

      .nav-icon {
        width: var(--icon-size-lg);
        height: var(--icon-size-lg);
        object-fit: contain;
        flex-shrink: 0;
        display: block;
      }

      .nav-label {
        font-size: var(--font-size-lg);
        line-height: 1.25;
        white-space: nowrap;
      }
    }
  }

  .history-area {
    flex: 1 1 0;
    min-height: 0;
    margin-top: 10px;
    /* 抵消侧栏左右 padding，让历史列表沾满可展示宽度 */
    margin-left: -12px;
    margin-right: -12px;
    width: calc(100% + 24px);
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .history-head {
      flex-shrink: 0;
      padding: 6px 12px 4px;
      font-size: var(--font-size-xs);
      font-weight: var(--font-weight-medium);
      color: var(--text-muted);
      letter-spacing: 0.04em;
    }

    :deep(.history-popup) {
      flex: 1 1 0;
      min-height: 0;
      height: auto;
      width: 100%;
      overflow: hidden;
    }
  }

  // 用户区域（紧凑条，避免占过高）
  .user-area {
    display: flex;
    align-items: center;
    min-height: 36px;
    padding: 6px 2px;
    border-top: 1px solid var(--border-subtle);
    margin-top: auto;
    flex-shrink: 0;

    .user-trigger {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      min-height: 28px;
      padding: 2px 4px;
      border: none;
      background: transparent;
      cursor: pointer;
      border-radius: var(--radius-sm);
      text-align: left;

      &:hover {
        background: rgba(59, 130, 246, 0.08);
      }
    }

    .user-avatar {
      width: 24px !important;
      height: 24px !important;
      line-height: 24px !important;
      background: #c5cdd6;
      flex-shrink: 0;
      overflow: hidden;

      > img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        object-position: center 22%;
        display: block;
      }
    }

    .user-name {
      font-size: var(--font-size-sm);
      line-height: 1.2;
      color: var(--text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}
</style>
