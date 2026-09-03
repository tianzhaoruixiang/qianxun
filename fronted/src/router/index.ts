import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { useUserProfileStore } from '@/stores/useUserProfileStore'

const ADMIN_ROUTE_NAMES = new Set([
  'skill-market',
  'tool-market',
  'plugin-market',
  'mcp-market',
])

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import(/* webpackChunkName: "space" */ '@/views/SpaceView.vue'),
        },
        {
          path: 'chat/:sessionId?',
          name: 'chat',
          component: () => import(/* webpackChunkName: "chat" */ '@/views/ChatView.vue'),
        },
        {
          path: 'market',
          name: 'market',
          component: () => import(/* webpackChunkName: "market" */ '@/views/MarketView.vue'),
        },
        {
          path: 'market/skills',
          name: 'skill-market',
          component: () => import(/* webpackChunkName: "market" */ '@/views/SkillsMarketView.vue'),
        },
        {
          path: 'market/tools',
          name: 'tool-market',
          component: () => import(/* webpackChunkName: "market" */ '@/views/ToolsMarketView.vue'),
        },
        {
          path: 'market/plugins',
          name: 'plugin-market',
          component: () => import(/* webpackChunkName: "market" */ '@/views/PluginsMarketView.vue'),
        },
        {
          path: 'market/mcp',
          name: 'mcp-market',
          component: () => import(/* webpackChunkName: "market" */ '@/views/McpPluginsView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')?.trim()
  if (to.path === '/login') {
    if (token) {
      return { path: '/chat' }
    }
    return true
  }
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (ADMIN_ROUTE_NAMES.has(String(to.name))) {
    const userProfile = useUserProfileStore()
    if (!userProfile.profile) await userProfile.ensureLoaded()
    if (!userProfile.isAdmin) return { path: '/chat' }
  }
  return true
})

export default router
