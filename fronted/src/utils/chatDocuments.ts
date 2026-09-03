/** 聊天里可预览/下载的办公文档后缀 */
export const CHAT_DOC_EXTS = ['xlsx', 'xls', 'md', 'doc', 'docx'] as const

export type ChatDocExt = (typeof CHAT_DOC_EXTS)[number]

export interface ChatDocumentRef {
  name: string
  href: string
  publicToken?: string
  ext: ChatDocExt
}

const PUBLIC_FILE_RE =
  /(?:https?:\/\/[^/\s)]+)?\/QianXunService\/data\/files\/public\/([A-Za-z0-9_-]+)/i

/** 入库 token 为去横线 UUID（32 位 hex），排除模型编造的文件名路径 */
function isStoredPublicToken(token: string): boolean {
  return /^[a-fA-F0-9]{32}$/.test(token)
}

const MD_LINK_RE = /\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g

function extOf(nameOrUrl: string): string {
  const clean = nameOrUrl.split('?')[0].split('#')[0].trim()
  const base = clean.split('/').pop() || clean
  const dot = base.lastIndexOf('.')
  return dot >= 0 ? base.slice(dot + 1).toLowerCase() : ''
}

export function isChatDocumentExt(ext: string): ext is ChatDocExt {
  return (CHAT_DOC_EXTS as readonly string[]).includes(ext.toLowerCase())
}

export function extractPublicToken(href: string): string {
  const m = href.match(PUBLIC_FILE_RE)
  return m?.[1] || ''
}

export function rewritePublicFileHref(href: string): string {
  const token = extractPublicToken(href)
  if (!token) return href
  return `/QianXunService/data/files/public/${encodeURIComponent(token)}`
}

function filenameFromHref(href: string, fallback: string): string {
  try {
    const path = href.split('?')[0]
    const last = decodeURIComponent(path.split('/').pop() || '')
    if (last && last.includes('.')) return last
  } catch {
    /* ignore */
  }
  return fallback || '文档'
}

export function collectChatDocuments(content: string): ChatDocumentRef[] {
  const seen = new Set<string>()
  const out: ChatDocumentRef[] = []

  function add(name: string, rawHref: string) {
    const href = rewritePublicFileHref(rawHref.trim())
    const rawToken = extractPublicToken(href)
    const token = rawToken && isStoredPublicToken(rawToken) ? rawToken : ''
    const ext = extOf(name) || extOf(href)
    const known = isChatDocumentExt(ext)
    const isPublic = Boolean(token)
    const isAppDoc = href.startsWith('/QianXunService/') || href.startsWith('/')
    if (!isPublic && !(known && isAppDoc && !rawToken)) return
    const key = token || `${name}|${href}`
    if (seen.has(key)) return
    seen.add(key)
    out.push({
      name: name.trim() || filenameFromHref(href, known ? `文档.${ext}` : '文档'),
      href,
      publicToken: token || undefined,
      ext: (known ? ext : 'md') as ChatDocExt,
    })
  }

  const text = content || ''
  MD_LINK_RE.lastIndex = 0
  let m: RegExpExecArray | null
  while ((m = MD_LINK_RE.exec(text))) {
    add(m[1], m[2])
  }

  const urlRe = /(?:https?:\/\/[^\s)]+|\/QianXunService\/data\/files\/public\/[A-Za-z0-9_-]+)/gi
  let u: RegExpExecArray | null
  while ((u = urlRe.exec(text))) {
    const href = u[0]
    add(filenameFromHref(href, ''), href)
  }

  return out
}

export function kindFromChatDocExt(ext: string): string {
  const e = ext.toLowerCase()
  if (e === 'doc' || e === 'docx') return 'word'
  if (e === 'xls' || e === 'xlsx') return 'excel'
  if (e === 'md') return 'text'
  return 'file'
}
