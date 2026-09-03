import request from '@/utils/request'

export interface HermesProfileItem {
  name: string
  description?: string
  model?: string
  active?: boolean
  path?: string
  contextWindow?: number
}

export function listHermesProfiles(): Promise<HermesProfileItem[]> {
  return request.post('/hermes/profiles/list', { jsonArg: {} })
}

export function createHermesProfile(name: string, description?: string): Promise<{
  ok: boolean
  name: string
  path?: string
  alreadyExists?: boolean
  message?: string
}> {
  return request.post('/hermes/profiles/create', { jsonArg: { name, description } })
}

export interface HermesSoul {
  name: string
  content: string
  exists: boolean
}

export function getHermesSoul(name: string): Promise<HermesSoul> {
  return request.post('/hermes/profiles/soul', { jsonArg: { name } })
}

export function updateHermesSoul(name: string, content: string): Promise<HermesSoul> {
  return request.post('/hermes/profiles/soul/update', { jsonArg: { name, content } })
}

export interface HermesSkillItem {
  name: string
  description?: string
  category?: string
  enabled: boolean
  provenance?: string
}

export interface HermesSkillFileNode {
  path: string
  name: string
  directory: boolean
  size?: number
  text: boolean
}

export interface HermesSkillFile {
  path: string
  content: string
  text: boolean
  name: string
}

export interface HermesSkillUploadResult {
  ok: boolean
  installed: string[]
  errors: string[]
}

export function listHermesSkills(profile: string): Promise<HermesSkillItem[]> {
  return request.post('/hermes/skills/list', { jsonArg: { profile } })
}

export function listHermesSkillTree(profile: string, name: string): Promise<HermesSkillFileNode[]> {
  return request.post('/hermes/skills/tree', { jsonArg: { profile, name } })
}

export function readHermesSkillFile(profile: string, name: string, path: string): Promise<HermesSkillFile> {
  return request.post('/hermes/skills/file', { jsonArg: { profile, name, path } })
}

export function updateHermesSkillFile(
  profile: string,
  name: string,
  path: string,
  content: string,
): Promise<HermesSkillFile> {
  return request.post('/hermes/skills/file/update', { jsonArg: { profile, name, path, content } })
}

export function toggleHermesSkill(profile: string, name: string, enabled: boolean): Promise<{ ok: boolean; name: string; enabled: boolean }> {
  return request.post('/hermes/skills/toggle', { jsonArg: { profile, name, enabled } })
}

export async function uploadHermesSkillZip(profile: string, file: File): Promise<HermesSkillUploadResult> {
  const form = new FormData()
  form.append('file', file, file.name)
  if (profile) form.append('profile', profile)
  return (await request.http.post('/hermes/skills/upload', form, { timeout: 180000 })) as HermesSkillUploadResult
}

export async function downloadHermesSkillZip(profile: string, name: string): Promise<void> {
  const blob = (await request.http.get('/hermes/skills/download', {
    params: { profile, name },
    responseType: 'blob',
    timeout: 180000,
  })) as Blob
  if (blob.type && blob.type.includes('json')) {
    const text = await blob.text()
    let msg = '下载失败'
    try {
      const parsed = JSON.parse(text) as { message?: string }
      if (parsed.message) msg = parsed.message
    } catch {
      /* 非 JSON */
    }
    throw new Error(msg)
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${name || 'skill'}.zip`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export interface HermesToolItem {
  name: string
  displayName?: string
  iconKind?: string
  enabled?: boolean
}

export interface HermesToolsetItem {
  name: string
  label?: string
  description?: string
  platform?: string
  platformLabel?: string
  enabled: boolean
  configured?: boolean
  tools?: HermesToolItem[]
}

function flagOn(value: unknown): boolean {
  return value === true
}

function normalizeToolset(item: HermesToolsetItem): HermesToolsetItem {
  const enabled = flagOn(item.enabled)
  return {
    ...item,
    enabled,
    tools: (item.tools || []).map((tool) => ({
      ...tool,
      enabled: tool.enabled === undefined ? enabled : flagOn(tool.enabled),
    })),
  }
}

export async function listHermesToolsets(profile: string): Promise<HermesToolsetItem[]> {
  const data = await request.post<HermesToolsetItem[]>('/hermes/tools/list', { jsonArg: { profile } })
  return (Array.isArray(data) ? data : []).map(normalizeToolset)
}

export async function toggleHermesToolset(
  profile: string,
  name: string,
  enabled: boolean,
): Promise<{ ok: boolean; name: string; enabled: boolean }> {
  const r = await request.post<{ ok: boolean; name: string; enabled: boolean }>('/hermes/tools/toggle', {
    jsonArg: { profile, name, enabled },
  })
  return {
    ok: Boolean(r?.ok),
    name: r?.name || name,
    enabled: typeof r?.enabled === 'boolean' ? r.enabled : enabled,
  }
}

export interface HermesLiveTaskLog {
  index: number
  path?: string
  goal?: string
  status?: string
  size?: number
}

export interface HermesLiveDelegation {
  delegationId: string
  path?: string
  started?: string
  completed?: string
  taskCount: number
  tasks: HermesLiveTaskLog[]
}

export interface HermesLiveTranscriptContent {
  ok: boolean
  delegationId: string
  taskIndex?: number | null
  path?: string
  content: string
  message?: string
}

/** 列出当前 profile 下最近的子智能体 live transcript。 */
export function listHermesLiveTranscripts(
  profile: string,
  limit = 8,
): Promise<HermesLiveDelegation[]> {
  return request.post('/hermes/delegation/live/list', { jsonArg: { profile, limit } })
}

/** 读取某一委派的 live transcript（可指定 taskIndex）。 */
export function readHermesLiveTranscript(
  profile: string,
  delegationId: string,
  taskIndex?: number | null,
  maxChars?: number,
): Promise<HermesLiveTranscriptContent> {
  return request.post('/hermes/delegation/live/read', {
    jsonArg: { profile, delegationId, taskIndex, maxChars },
  })
}

