import request from '@/utils/request'

export interface UserProfile {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
  enabled: boolean
  role: 'admin' | 'functional' | string
}

export interface CreateUserPayload {
  username: string
  password: string
  displayName?: string
}

/** POST /QianXunService/users/me — 当前用户 */
export function fetchCurrentUser(): Promise<UserProfile> {
  return request.post('/users/me', { jsonArg: {} })
}

/** POST /QianXunService/users/create — 管理员创建功能用户 */
export function createFunctionalUser(payload: CreateUserPayload): Promise<UserProfile> {
  return request.post('/users/create', { jsonArg: payload })
}
