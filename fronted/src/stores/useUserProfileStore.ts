import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchCurrentUser, type UserProfile } from '@/api/user'

/**
 * 当前用户展示信息（与后端 UserContext 一致，可由 localStorage + 请求头驱动）。
 */
export const useUserProfileStore = defineStore('userProfile', () => {
  const profile = ref<UserProfile | null>(null)
  const loading = ref(false)

  const displayLabel = computed(() => {
    const p = profile.value
    if (!p) return '访客'
    const d = p.displayName?.trim()
    if (d) return d
    const u = p.username?.trim()
    if (u) return u
    return `用户 ${p.id}`
  })

  const isAdmin = computed(() => profile.value?.role === 'admin')

  async function ensureLoaded() {
    if (loading.value) return
    loading.value = true
    try {
      profile.value = await fetchCurrentUser()
    } catch {
      profile.value = null
    } finally {
      loading.value = false
    }
  }

  /** 写入浏览器本地身份（下次请求会带到 X-User-* 头里） */
  function saveLocalIdentity(values: {
    userId?: string
    userName?: string
    userDisplayName?: string
    role?: string
  }) {
    if (values.userId !== undefined) {
      const v = values.userId.trim()
      if (v) {
        localStorage.setItem('userId', v)
        localStorage.removeItem('qianxunUserId')
      } else {
        localStorage.removeItem('userId')
        localStorage.removeItem('qianxunUserId')
      }
    }
    if (values.userName !== undefined) {
      const v = values.userName.trim()
      if (v) localStorage.setItem('userName', v)
      else localStorage.removeItem('userName')
    }
    if (values.userDisplayName !== undefined) {
      const v = values.userDisplayName.trim()
      if (v) localStorage.setItem('userDisplayName', v)
      else localStorage.removeItem('userDisplayName')
    }
    if (values.role !== undefined && values.userId && values.userName) {
      profile.value = {
        id: values.userId,
        username: values.userName,
        displayName: values.userDisplayName || values.userName,
        avatarUrl: null,
        enabled: true,
        role: values.role,
      }
    }
    void ensureLoaded()
  }

  function logout() {
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('qianxunUserId')
    localStorage.removeItem('userName')
    localStorage.removeItem('userDisplayName')
    profile.value = null
  }

  return {
    profile,
    loading,
    displayLabel,
    isAdmin,
    ensureLoaded,
    saveLocalIdentity,
    logout,
  }
})
