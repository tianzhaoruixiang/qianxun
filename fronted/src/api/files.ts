import request, { apiBaseURL } from '@/utils/request'

export interface DataFileRow {
  id: string
  name: string
  date?: string
  displayDate?: string
  kind: string
  preview?: string
  publicUrl?: string
  publicToken?: string
  contentType?: string
  sizeBytes?: number
  folderPath?: string
}

export interface DataFileDetail {
  id: string
  name: string
  date: string
  kind: string
  detailText: string
  excelRows: string[][]
  publicUrl?: string
  publicToken?: string
  contentType?: string
  sizeBytes?: number
  folderPath?: string
}

export interface BatchUploadResult {
  files: DataFileRow[]
  errors: string[]
  ok: number
  fail: number
}

export interface UploadFileItem {
  file: File
  /** 相对路径，如 folder/sub/a.txt（目录上传） */
  relativePath?: string
}

const BATCH_SIZE = 40
const CONCURRENCY = 4
const UPLOAD_ACCEPT =
  '.doc,.docx,.xls,.xlsx,.csv,.pdf,.txt,.md,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.webp,.bmp,.svg,.zip'

export function uploadAcceptAttr(): string {
  return UPLOAD_ACCEPT
}

export function isFolderRow(f: Pick<DataFileRow, 'kind'>): boolean {
  return (f.kind || '').toLowerCase() === 'folder'
}

export function listDataFiles(): Promise<DataFileRow[]> {
  return request.post('/data/files/list', { jsonArg: {} })
}

export function fetchDataFileDetail(id: string): Promise<DataFileDetail> {
  return request.post('/data/files/detail', { jsonArg: { id: id.trim() } })
}

export function fetchDataFileDetailByToken(token: string): Promise<DataFileDetail> {
  const t = token.trim()
  return request.get(`/data/files/public/${encodeURIComponent(t)}/detail`)
}

function toUploadItems(files: File[]): UploadFileItem[] {
  return files.map((file) => {
    const rel = (file as File & { webkitRelativePath?: string }).webkitRelativePath?.trim()
    return { file, relativePath: rel || undefined }
  })
}

async function postUploadBatch(
  items: UploadFileItem[],
  folderPath?: string,
): Promise<BatchUploadResult> {
  const form = new FormData()
  for (const item of items) {
    form.append('files', item.file)
    form.append('relativePaths', item.relativePath || item.file.name)
  }
  if (folderPath) form.append('folderPath', folderPath)
  const out = await request.http.post('/data/files/upload-batch', form, { timeout: 600000 })
  return out as BatchUploadResult
}

/** 高性能批量上传：分片 + 有限并发；支持目录相对路径与 ZIP（服务端递归解压）。 */
export async function uploadDataFiles(
  files: File[],
  folderPath?: string,
  onProgress?: (done: number, total: number) => void,
): Promise<BatchUploadResult> {
  const items = toUploadItems(files).filter((it) => it.file && it.file.size >= 0)
  if (!items.length) {
    return { files: [], errors: ['请选择要上传的文件'], ok: 0, fail: 0 }
  }

  const chunks: UploadFileItem[][] = []
  for (let i = 0; i < items.length; i += BATCH_SIZE) {
    chunks.push(items.slice(i, i + BATCH_SIZE))
  }

  const merged: BatchUploadResult = { files: [], errors: [], ok: 0, fail: 0 }
  let finishedFiles = 0
  const total = items.length

  let next = 0
  async function worker() {
    while (next < chunks.length) {
      const idx = next++
      const chunk = chunks[idx]
      const result = await postUploadBatch(chunk, folderPath)
      merged.files.push(...(result.files || []))
      merged.errors.push(...(result.errors || []))
      merged.ok += result.ok || 0
      merged.fail += result.fail || 0
      finishedFiles += chunk.length
      onProgress?.(Math.min(finishedFiles, total), total)
    }
  }

  const workers = Array.from({ length: Math.min(CONCURRENCY, chunks.length) }, () => worker())
  await Promise.all(workers)
  return merged
}

export function createFolder(name: string, parentPath?: string): Promise<DataFileRow> {
  return request.post('/data/folders/create', {
    jsonArg: { name: name.trim(), parentPath: parentPath || '' },
  })
}

export function deleteDataFile(id: string): Promise<number> {
  return request.post('/data/files/delete', { jsonArg: { id: id.trim() } })
}

export function deleteFolder(path: string): Promise<number> {
  return request.post('/data/folders/delete', { jsonArg: { path } })
}

export async function downloadDataFile(id: string, filename: string): Promise<void> {
  const blob = (await request.http.get(`/data/files/download/${encodeURIComponent(id)}`, {
    responseType: 'blob',
    timeout: 180000,
  })) as Blob
  saveBlob(await ensureDownloadBlob(blob), filename)
}

export async function downloadPublicFile(token: string, filename: string): Promise<void> {
  const blob = (await request.http.get(`/data/files/public/${encodeURIComponent(token)}`, {
    responseType: 'blob',
    timeout: 180000,
  })) as Blob
  saveBlob(await ensureDownloadBlob(blob), filename)
}

async function ensureDownloadBlob(blob: Blob): Promise<Blob> {
  const type = (blob.type || '').toLowerCase()
  if (!type.includes('application/json')) {
    return blob
  }
  const text = await blob.text()
  try {
    const parsed = JSON.parse(text) as { message?: string }
    throw new Error(parsed.message || '下载失败')
  } catch (e) {
    if (e instanceof SyntaxError) {
      throw new Error('下载失败')
    }
    throw e
  }
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || 'download'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function publicFilePath(token: string | undefined): string {
  if (!token?.trim()) return ''
  return `${apiBaseURL}/data/files/public/${encodeURIComponent(token.trim())}`
}
