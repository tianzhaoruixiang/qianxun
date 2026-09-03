<template>
  <div class="file-list">
    <div v-if="!store.keyword.trim()" class="crumbs">
      <button type="button" class="crumb" :class="{ active: !store.currentPath }" @click="store.goTo('')">
        全部文件
      </button>
      <template v-for="c in store.crumbs" :key="c.path">
        <span class="sep">/</span>
        <button type="button" class="crumb" :class="{ active: c.path === store.currentPath }" @click="store.goTo(c.path)">
          {{ c.name }}
        </button>
      </template>
    </div>
    <div v-else class="crumbs muted">搜索结果</div>

    <div v-if="store.loading" class="hint">加载中…</div>
    <div v-else-if="!store.filteredFiles.length" class="empty-files">
      <AppGlyph name="empty" size="lg" class="empty-glyph" />
      <span>{{ emptyHint }}</span>
    </div>
    <ul v-else class="list">
      <li
        v-for="f in store.filteredFiles"
        :key="f.id"
        class="item"
        :class="{ folder: isFolder(f) }"
      >
        <span
          class="kind-icon"
          :title="kindTitle(f.kind)"
        >
          <FileTypeIcon :kind="f.kind" />
        </span>
        <div class="main" @click="onMainClick(f)">
          <span class="name" :title="itemTitle(f)">{{ f.name }}</span>
          <span v-if="searchPathOf(f)" class="preview" :title="searchPathOf(f)">{{ searchPathOf(f) }}</span>
          <span v-else-if="f.preview" class="preview" :title="f.preview">{{ f.preview }}</span>
        </div>
        <span class="date">{{ displayDateOf(f) }}</span>
        <a-button
          v-if="!isFolder(f)"
          type="text"
          size="small"
          class="preview-btn"
          title="下载"
          aria-label="下载"
          :loading="downloadingId === f.id"
          @click.stop="onDownload(f)"
        >
          <AppGlyph name="download" size="sm" />
        </a-button>
        <span v-else class="preview-btn" aria-hidden="true"></span>
        <a-button
          v-if="!isFolder(f)"
          type="text"
          size="small"
          class="preview-btn"
          title="预览"
          aria-label="预览"
          @click.stop="openDetail(f.id)"
        >
          <AppGlyph name="preview" size="sm" />
        </a-button>
        <span v-else class="preview-btn" aria-hidden="true"></span>
        <a-button
          type="text"
          size="small"
          class="preview-btn danger"
          title="删除"
          aria-label="删除"
          @click.stop="onDelete(f)"
        >
          <AppGlyph name="delete" size="sm" />
        </a-button>
      </li>
    </ul>

    <a-modal
      v-model:open="detailOpen"
      width="min(720px, 92vw)"
      title="文件预览"
      :footer="null"
      destroy-on-close
      @cancel="closeDetail"
    >
      <div v-if="detailLoading" class="hint">加载中…</div>
      <div v-else-if="detail" class="detail-wrap">
        <dl class="detail-meta">
          <div class="row">
            <dt>文件类型</dt>
            <dd>{{ detail.kind }}</dd>
          </div>
          <div class="row">
            <dt>日期</dt>
            <dd>{{ detail.date }}</dd>
          </div>
          <div v-if="detail.folderPath" class="row">
            <dt>目录</dt>
            <dd>{{ detail.folderPath }}</dd>
          </div>
          <div v-if="detail.sizeBytes != null" class="row">
            <dt>大小</dt>
            <dd>{{ formatSize(detail.sizeBytes) }}</dd>
          </div>
          <div v-if="detail.publicToken" class="row">
            <dt>公开链接</dt>
            <dd>
              <a :href="publicFilePath(detail.publicToken)" target="_blank" rel="noopener noreferrer">打开原文件</a>
            </dd>
          </div>
        </dl>
        <div class="detail-title">{{ detail.name }}</div>
        <img
          v-if="detail.kind === 'image' && detail.publicToken"
          class="detail-image"
          :src="publicFilePath(detail.publicToken)"
          :alt="detail.name"
        />
        <pre class="detail-body">{{ detail.detailText || '（无正文）' }}</pre>
      </div>
      <div v-else class="hint">加载失败</div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import FileTypeIcon from '@/components/FileTypeIcon.vue'
import AppGlyph from '@/components/AppGlyph.vue'
import { useDataFilesStore } from '@/stores/useDataFilesStore'
import {
  downloadDataFile,
  fetchDataFileDetail,
  isFolderRow,
  publicFilePath,
  type DataFileDetail,
  type DataFileRow,
} from '@/api/files'

const store = useDataFilesStore()

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<DataFileDetail | null>(null)
const downloadingId = ref('')

const emptyHint = computed(() => {
  if (store.keyword.trim()) return '没有匹配的文件'
  if (store.currentPath) return '此文件夹为空'
  return '暂无文件，可新建文件夹或上传文件/文件夹/压缩包'
})

function isFolder(f: DataFileRow): boolean {
  return isFolderRow(f)
}

function normalizeKind(kind: string | undefined): string {
  return (kind ?? '').trim().toLowerCase()
}

function kindTitle(kind: string | undefined): string {
  const k = normalizeKind(kind)
  if (k === 'folder') return '文件夹'
  if (k === 'word' || k === 'doc' || k === 'docx') return 'Word 文档'
  if (k === 'excel' || k === 'xls' || k === 'xlsx' || k === 'csv') return 'Excel 表格'
  if (k === 'pdf') return 'PDF'
  if (k === 'ppt' || k === 'pptx') return 'PPT'
  if (k === 'text' || k === 'txt' || k === 'md') return '文本'
  if (k === 'image') return '图片'
  if (k === 'eml') return '邮件'
  if (k === 'archive' || k === 'zip') return '压缩包'
  if (k === 'voice') return '语音'
  if (k === 'person') return '人物'
  if (k === 'organization') return '机构'
  if (k === 'news') return '新闻'
  return kind?.trim() ? `类型：${kind}` : '文件'
}

function displayDateOf(f: DataFileRow): string {
  return f.displayDate || f.date || ''
}

function itemTitle(f: DataFileRow): string {
  if (isFolder(f)) return `打开文件夹 ${f.name}`
  return f.name
}

function searchPathOf(f: DataFileRow): string {
  if (!store.keyword.trim()) return ''
  const p = f.folderPath || ''
  return p ? `位置：${p}` : ''
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function onMainClick(f: DataFileRow) {
  if (isFolder(f)) {
    if (store.keyword.trim()) {
      store.goTo(f.folderPath ? `${f.folderPath}/${f.name}` : f.name)
    } else {
      store.enterFolder(f.name)
    }
    return
  }
  void openDetail(f.id)
}

async function openDetail(id: string) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchDataFileDetail(id)
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailOpen.value = false
}

async function onDownload(f: DataFileRow) {
  downloadingId.value = f.id
  try {
    await downloadDataFile(f.id, f.name)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载失败')
  } finally {
    downloadingId.value = ''
  }
}

function onDelete(f: DataFileRow) {
  const isDir = isFolder(f)
  Modal.confirm({
    title: isDir ? '删除文件夹' : '删除文件',
    content: isDir
      ? `确定删除文件夹「${f.name}」及其内部全部文件？此操作不可恢复。`
      : `确定删除「${f.name}」？此操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await store.removeItem(f)
        message.success('已删除')
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
        throw e
      }
    },
  })
}
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';

.file-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  padding: 0 0 8px;
  font-size: var(--font-size-xs);
  flex-shrink: 0;

  &.muted {
    color: var(--text-muted, #94a3b8);
    padding-bottom: 8px;
  }

  .sep {
    color: var(--text-muted, #94a3b8);
    padding: 0 2px;
  }

  .crumb {
    border: 0;
    background: none;
    padding: 0 4px;
    cursor: pointer;
    color: var(--color-primary, #0ea5e9);
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    &.active {
      color: var(--panel-text, #334155);
      font-weight: var(--font-weight-semibold);
      cursor: default;
    }
  }
}

.hint {
  font-size: var(--font-size-xs);
  color: var(--text-muted, #94a3b8);
  padding: 8px 0;
}

.empty-files {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 28px 12px;
  color: var(--text-muted, #94a3b8);
  font-size: var(--font-size-xs);
  text-align: center;

  .empty-glyph {
    width: 32px;
    height: 32px;
  }
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto 28px 28px 28px;
  gap: 6px 8px;
  align-items: center;
  font-size: var(--font-size-xs);
  padding: 4px 6px;
  height: 40px;
  box-sizing: border-box;
  border-radius: 8px;
  overflow: hidden;

  &:hover {
    background: rgba(59, 130, 246, 0.06);
  }

  &.folder .main {
    cursor: pointer;
  }

  .kind-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: none;
    line-height: 0;
    padding-top: 0;

    :deep(.ft-icon) {
      width: 28px;
      height: 28px;
    }
  }

  .main {
    min-width: 0;
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    overflow: hidden;
  }

  .name {
    flex: 0 1 auto;
    max-width: 100%;
    color: var(--panel-text, #334155);
    font-weight: var(--font-weight-medium);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    line-height: 1.3;
  }

  .preview {
    flex: 1 1 0;
    min-width: 0;
    font-size: var(--font-size-xs);
    line-height: 1.3;
    color: var(--text-muted, #64748b);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .date {
    color: var(--text-muted, #94a3b8);
    flex: none;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
    line-height: 1.25;
    align-self: center;
  }

  .preview-btn {
    @include icon-btn(var(--icon-btn-size-sm));
    color: var(--panel-text, #334155);
    opacity: 0.82;
    border: none;
    background: transparent;
    border-radius: 6px;
    cursor: pointer;

    &:hover {
      opacity: 1;
      color: var(--color-primary, #0ea5e9);
      background: rgba(59, 130, 246, 0.08);
    }

    &.danger:hover {
      color: #dc2626;
      background: rgba(220, 38, 38, 0.08);
    }
  }
}

.detail-wrap {
  max-height: min(60vh, 520px);
  overflow: auto;
}

.detail-meta {
  margin: 0 0 12px;
  font-size: var(--font-size-xs);

  .row {
    display: grid;
    grid-template-columns: 72px 1fr;
    gap: 6px 10px;
    margin-bottom: 6px;
    align-items: start;
  }

  dt {
    margin: 0;
    color: var(--text-muted, #64748b);
  }

  dd {
    margin: 0;
    color: var(--panel-text, #334155);
    word-break: break-all;
  }
}

.detail-title {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-md);
  margin-bottom: 8px;
  color: var(--panel-text, #0f172a);
}

.detail-image {
  display: block;
  max-width: 100%;
  max-height: 360px;
  margin: 0 0 12px;
  border-radius: 8px;
  object-fit: contain;
  background: rgba(15, 23, 42, 0.04);
}

.detail-body {
  margin: 0;
  padding: 10px 12px;
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(15, 23, 42, 0.04);
  border-radius: 8px;
  color: var(--panel-text, #334155);
  font-family: var(--font-family-base);
}
</style>
