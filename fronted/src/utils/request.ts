import axios, { AxiosHeaders } from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { buildAuthHeaders } from '@/utils/apiHeaders'
import { sanitizeUserFacingText } from '@/utils/userFacingCopy'

export const apiBaseURL = (import.meta.env.VITE_API_BASE_URL || '/QianXunService').replace(/\/$/, '')

const http: AxiosInstance = axios.create({
  baseURL: apiBaseURL,
  timeout: 120000,
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const headers = AxiosHeaders.from(config.headers)
  for (const [key, value] of Object.entries(buildAuthHeaders())) {
    headers.set(key, value)
  }
  config.headers = headers
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (typeof Blob !== 'undefined' && body instanceof Blob) {
      return body
    }
    if (body && typeof body === 'object' && 'code' in body) {
      const code = (body as { code: number }).code
      if (code !== 0) {
        return Promise.reject(new Error(sanitizeUserFacingText((body as { message?: string }).message || '请求失败')))
      }
      return (body as { data: unknown }).data
    }
    return body
  },
  async (err) => {
    const status = err?.response?.status
    const url = String(err?.config?.url || '')
    const isPublicFile = url.includes('/data/files/public/')
    if (status === 401 && !url.includes('/auth/login') && !isPublicFile) {
      localStorage.removeItem('token')
      const base = import.meta.env.BASE_URL.replace(/\/?$/, '/')
      window.location.assign(`${base}login`)
    }
    const data = err?.response?.data
    let msg = err?.message || '网络请求失败'
    if (typeof Blob !== 'undefined' && data instanceof Blob) {
      try {
        const text = await data.text()
        const parsed = JSON.parse(text) as { message?: string }
        if (parsed?.message) msg = parsed.message
      } catch {
        /* 非 JSON 错误体 */
      }
    } else if (data && typeof data === 'object' && 'message' in data && typeof (data as { message: unknown }).message === 'string') {
      msg = (data as { message: string }).message
    }
    return Promise.reject(new Error(sanitizeUserFacingText(typeof msg === 'string' ? msg : '网络请求失败')))
  },
)

async function post<T>(url: string, data?: unknown): Promise<T> {
  const out = await http.post(url, data ?? {})
  return out as T
}

async function get<T>(url: string): Promise<T> {
  const out = await http.get(url)
  return out as T
}

export default { post, get, http }
