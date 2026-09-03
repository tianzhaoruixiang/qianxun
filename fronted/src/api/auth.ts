import request from '@/utils/request'

export interface LoginResult {
  token: string
  expiresInSeconds: number
  userId: string
  username: string
  displayName: string
  role: string
}

/** POST /QianXunService/auth/login */
export function login(username: string, password: string): Promise<LoginResult> {
  return request.post('/auth/login', { username, password })
}
