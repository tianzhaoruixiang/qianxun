import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import './styles/variables.scss'
import './styles/global.css'
import './styles/antd.scss'
import './styles/animations.css'
import './assets/fonts/fonts.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 初始化主题（在 mount 前执行，避免闪烁）
import { useThemeStore } from '@/stores/useThemeStore'
const themeStore = useThemeStore()
themeStore.initTheme()

// 监听系统偏好变化
import { useTheme } from '@/composables/useTheme'
const { initSystemPreferenceListener } = useTheme()
initSystemPreferenceListener()

app.mount('#app')
