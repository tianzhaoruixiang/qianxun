import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createFolder as apiCreateFolder,
  deleteDataFile,
  deleteFolder as apiDeleteFolder,
  isFolderRow,
  listDataFiles,
  type DataFileRow,
} from '@/api/files'

export const useDataFilesStore = defineStore('dataFiles', () => {
  const files = ref<DataFileRow[]>([])
  const keyword = ref('')
  const loading = ref(false)
  const currentPath = ref('')

  const folderCount = computed(() => files.value.filter(isFolderRow).length)
  const fileCount = computed(() => files.value.filter((f) => !isFolderRow(f)).length)

  const crumbs = computed(() => {
    const path = currentPath.value
    if (!path) return [] as { name: string; path: string }[]
    const segs = path.split('/').filter(Boolean)
    const out: { name: string; path: string }[] = []
    let acc = ''
    for (const s of segs) {
      acc = acc ? `${acc}/${s}` : s
      out.push({ name: s, path: acc })
    }
    return out
  })

  const filteredFiles = computed(() => {
    const k = keyword.value.trim().toLowerCase()
    const path = currentPath.value
    let rows = files.value
    if (k) {
      rows = rows.filter((f) => {
        const hay = `${f.name} ${f.preview ?? ''} ${f.kind} ${f.folderPath ?? ''}`.toLowerCase()
        return hay.includes(k)
      })
    } else {
      rows = rows.filter((f) => (f.folderPath || '') === path)
    }
    const folders = rows.filter(isFolderRow).sort((a, b) => a.name.localeCompare(b.name, 'zh'))
    const rest = rows.filter((f) => !isFolderRow(f)).sort((a, b) => a.name.localeCompare(b.name, 'zh'))
    return [...folders, ...rest]
  })

  async function loadFiles() {
    loading.value = true
    try {
      files.value = await listDataFiles()
    } catch {
      files.value = []
    } finally {
      loading.value = false
    }
  }

  function enterFolder(name: string) {
    currentPath.value = currentPath.value ? `${currentPath.value}/${name}` : name
    keyword.value = ''
  }

  function goTo(path: string) {
    currentPath.value = path
    keyword.value = ''
  }

  function goUp() {
    const i = currentPath.value.lastIndexOf('/')
    currentPath.value = i < 0 ? '' : currentPath.value.slice(0, i)
    keyword.value = ''
  }

  async function createFolder(name: string) {
    const row = await apiCreateFolder(name, currentPath.value)
    await loadFiles()
    return row
  }

  async function removeItem(row: DataFileRow) {
    if (isFolderRow(row)) {
      const full = row.folderPath ? `${row.folderPath}/${row.name}` : row.name
      await apiDeleteFolder(full)
      if (currentPath.value === full || currentPath.value.startsWith(`${full}/`)) {
        currentPath.value = row.folderPath || ''
      }
    } else {
      await deleteDataFile(row.id)
    }
    await loadFiles()
  }

  return {
    files,
    keyword,
    loading,
    currentPath,
    crumbs,
    folderCount,
    fileCount,
    filteredFiles,
    loadFiles,
    enterFolder,
    goTo,
    goUp,
    createFolder,
    removeItem,
  }
})
