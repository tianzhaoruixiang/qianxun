/**
 * 与后端 UserContextInterceptor、Bearer 鉴权约定一致的公共请求头。
 * - X-User-Id / X-User-Name / X-User-Display-Name：值需 URI 编码（中文等）
 * - 可从 localStorage 写入：token（登录后 JWT）、userId | qianxunUserId、userName、userDisplayName
 */
export function buildAuthHeaders(extra?: Record<string, string | undefined>): Record<string, string> {
  const headers: Record<string, string> = {}

  const token = localStorage.getItem('token')
  if (token) headers.Authorization = `Bearer ${token}`

  const uid = localStorage.getItem('userId') ?? localStorage.getItem('qianxunUserId')
  if (uid?.trim()) headers['X-User-Id'] = encodeURIComponent(uid.trim())

  const userName = localStorage.getItem('userName')
  if (userName?.trim()) headers['X-User-Name'] = encodeURIComponent(userName.trim())

  const displayName = localStorage.getItem('userDisplayName')
  if (displayName?.trim()) headers['X-User-Display-Name'] = encodeURIComponent(displayName.trim())

  if (extra) {
    for (const [k, v] of Object.entries(extra)) {
      if (v !== undefined && v !== '') headers[k] = v
    }
  }

  return headers
}
