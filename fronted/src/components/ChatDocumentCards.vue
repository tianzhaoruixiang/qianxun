<template>
  <div v-if="docs.length" class="chat-docs">
    <div
      v-for="doc in docs"
      :key="doc.publicToken || doc.href"
      class="chat-doc-card"
    >
      <FileTypeIcon :kind="kindFromChatDocExt(doc.ext)" />
      <div class="meta">
        <div class="name" :title="doc.name">{{ doc.name }}</div>
        <div class="ext">{{ doc.ext.toUpperCase() }}</div>
      </div>
      <a-button
        type="text"
        size="small"
        class="act"
        title="下载"
        aria-label="下载"
        :loading="downloadingKey === docKey(doc)"
        @click="onDownload(doc)"
      >
        <AppGlyph name="download" size="sm" />
      </a-button>
      <a-button
        type="text"
        size="small"
        class="act"
        title="预览"
        aria-label="预览"
        @click="onPreview(doc)"
      >
        <AppGlyph name="preview" size="sm" />
      </a-button>
    </div>

    <a-modal
      v-model:open="previewOpen"
      width="min(720px, 92vw)"
      title="文件预览"
      :footer="null"
      destroy-on-close
      @cancel="closePreview"
    >
      <div v-if="previewLoading" class="hint">加载中…</div>
      <div v-else-if="previewError" class="hint">{{ previewError }}</div>
      <div v-else class="preview-wrap">
        <div class="preview-title">{{ previewName }}</div>
        <div v-if="previewExcel.length" class="table-scroll">
          <table>
            <tbody>
              <tr v-for="(row, ri) in previewExcel" :key="ri">
                <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else-if="previewHtml" class="md-body" v-html="previewHtml" />
        <pre v-else class="preview-body">{{ previewText || '（无正文）' }}</pre>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import FileTypeIcon from '@/components/FileTypeIcon.vue'
import AppGlyph from '@/components/AppGlyph.vue'
import {
  downloadPublicFile,
  fetchDataFileDetailByToken,
  publicFilePath,
} from '@/api/files'
import {
  collectChatDocuments,
  kindFromChatDocExt,
  type ChatDocumentRef,
} from '@/utils/chatDocuments'
import { renderMarkdown } from '@/utils/renderMarkdown'

const props = defineProps<{
  content: string
}>()

const docs = computed(() => collectChatDocuments(props.content))

const downloadingKey = ref('')
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewName = ref('')
const previewText = ref('')
const previewHtml = ref('')
const previewExcel = ref<string[][]>([])

function docKey(doc: ChatDocumentRef): string {
  return doc.publicToken || doc.href
}

async function onDownload(doc: ChatDocumentRef) {
  downloadingKey.value = docKey(doc)
  try {
    if (doc.publicToken) {
      await downloadPublicFile(doc.publicToken, doc.name)
      return
    }
    if (doc.href.startsWith('http://qianxun-backend') || doc.href.startsWith('https://qianxun-backend')) {
      throw new Error('链接不可用，请重新生成文档')
    }
    const url = doc.href.startsWith('/') ? doc.href : publicFilePath(doc.publicToken)
    const res = await fetch(url)
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const objectUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = doc.name
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(objectUrl)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载失败')
  } finally {
    downloadingKey.value = ''
  }
}

async function onPreview(doc: ChatDocumentRef) {
  previewOpen.value = true
  previewLoading.value = true
  previewError.value = ''
  previewName.value = doc.name
  previewText.value = ''
  previewHtml.value = ''
  previewExcel.value = []
  try {
    if (doc.publicToken) {
      const detail = await fetchDataFileDetailByToken(doc.publicToken)
      previewName.value = detail.name || doc.name
      if (detail.excelRows?.length) {
        previewExcel.value = detail.excelRows
      }
      if (doc.ext === 'md' || detail.kind === 'text') {
        previewHtml.value = renderMarkdown(detail.detailText || '')
      } else {
        previewText.value = detail.detailText || ''
      }
      return
    }
    previewError.value = '暂无法在线预览该文件，请下载后查看'
  } catch (e) {
    previewError.value = e instanceof Error ? e.message : '预览失败'
  } finally {
    previewLoading.value = false
  }
}

function closePreview() {
  previewOpen.value = false
}
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';

.chat-docs {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.chat-doc-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 32px 32px;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 12px 14px;
  border-radius: var(--radius-md, 16px);
  background: var(--card-bg-solid, rgba(255, 255, 255, 0.92));
  border: 1px solid var(--card-border, rgba(0, 0, 0, 0.04));
  box-shadow: var(--card-shadow);
  box-sizing: border-box;
  transition:
    box-shadow 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.28s ease,
    transform 0.28s cubic-bezier(0.22, 1, 0.36, 1);

  &:hover {
    border-color: var(--card-border-hover, rgba(0, 0, 0, 0.08));
    box-shadow: var(--card-shadow-hover);
    transform: translateY(-1px);
  }
}

.meta {
  min-width: 0;
}

.name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ext {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.act {
  @include icon-btn(var(--icon-btn-size));
  color: var(--text-primary);
  opacity: 0.6;
  border-radius: 6px;

  &:hover {
    opacity: 1;
    color: #3866f5;
    background: rgba(56, 102, 245, 0.08);
  }
}

.hint {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.preview-wrap {
  max-height: min(60vh, 520px);
  overflow: auto;
  @include hide-scrollbar;
}

.preview-title {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-md);
  margin-bottom: 8px;
}

.preview-body {
  margin: 0;
  padding: 10px 12px;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(15, 23, 42, 0.04);
  border-radius: 8px;
}

.table-scroll {
  overflow: auto;
  @include hide-scrollbar;
}

table {
  border-collapse: collapse;
  font-size: var(--font-size-xs);
}

th, td {
  border: 1px solid #e5e7eb;
  padding: 6px 10px;
  white-space: nowrap;
}

.md-body {
  font-size: var(--font-size-md);
  line-height: var(--line-height-relaxed);
}
</style>
