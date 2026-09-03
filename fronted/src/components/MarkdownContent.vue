<template>
  <div class="chat-md markdown-body" v-html="html" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/renderMarkdown'

const props = defineProps<{
  content: string
  previewId?: string
}>()

const html = computed(() => renderMarkdown(props.content || ''))
</script>

<style scoped lang="scss">
.chat-md {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  // 不在整段回答上建滚动容器，否则纵向滚轮无法传到 .chat-body
  font-size: var(--font-size-md);
  line-height: var(--line-height-relaxed);
  letter-spacing: var(--letter-spacing-body);
  word-break: break-word;
  overflow-wrap: anywhere;
  color: inherit;
}

.markdown-body {
  :deep(> :first-child) {
    margin-top: 0;
  }

  :deep(> :last-child) {
    margin-bottom: 0;
  }

  :deep(p) {
    margin: 0 0 8px;
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 12px 0 8px;
    font-weight: var(--font-weight-semibold);
    line-height: var(--line-height-tight);
  }

  :deep(h1) { font-size: 1.35em; }
  :deep(h2) { font-size: 1.2em; }
  :deep(h3) { font-size: 1.08em; }

  :deep(ul),
  :deep(ol) {
    margin: 0 0 8px;
    padding-left: 22px;
  }

  :deep(li + li) {
    margin-top: 4px;
  }

  :deep(blockquote) {
    margin: 8px 0;
    padding: 4px 0 4px 10px;
    border-left: 3px solid rgba(56, 102, 245, 0.45);
    color: var(--text-secondary);
  }

  :deep(hr) {
    margin: 12px 0;
    border: none;
    border-top: 1px solid var(--border-subtle, #e5e7eb);
  }

  :deep(a) {
    color: #3866f5;
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  :deep(a.chat-doc-link) {
    font-weight: var(--font-weight-semibold);
  }

  :deep(img) {
    display: block;
    max-width: 100%;
    height: auto;
    border-radius: 6px;
    content-visibility: auto;
    contain-intrinsic-size: 160px 120px;
  }

  :deep(code) {
    font-family: var(--font-family-mono);
    font-size: var(--font-size-xs);
    background: rgba(15, 23, 42, 0.06);
    padding: 1px 5px;
    border-radius: 4px;
    word-break: break-word;
    overflow-wrap: anywhere;
  }

  :deep(pre) {
    margin: 8px 0;
    padding: 10px 12px;
    max-width: 100%;
    box-sizing: border-box;
    border-radius: 8px;
    background: rgba(15, 23, 42, 0.06);
    overflow-x: auto;
    overflow-y: hidden;
    overscroll-behavior-x: contain;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  // :deep + 伪元素分开写，避免 scoped 下 mixin 的 &::-webkit-scrollbar 失效露出 6px 条
  :deep(pre::-webkit-scrollbar),
  :deep(.table-wrap::-webkit-scrollbar) {
    width: 0 !important;
    height: 0 !important;
    display: none !important;
  }

  :deep(pre code) {
    background: transparent;
    padding: 0;
    font-size: var(--font-size-xs);
    white-space: pre;
    word-break: normal;
    overflow-wrap: normal;
  }

  :deep(.table-wrap) {
    display: block;
    width: 100%;
    max-width: 100%;
    margin: 10px 0;
    overflow-x: auto;
    overflow-y: hidden;
    overscroll-behavior-x: contain;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  :deep(table) {
    width: 100%;
    max-width: 100%;
    margin: 10px 0;
    border-collapse: collapse;
    table-layout: auto;
  }

  :deep(.table-wrap table) {
    margin: 0;
  }

  // 单元格可折行，表格宽度才能收敛到气泡内；否则长内容会把表格撑到被 .table-wrap 裁掉
  :deep(th),
  :deep(td) {
    padding: 8px 12px;
    border: 1px solid var(--border-subtle, #e5e7eb);
    background: #fff;
    white-space: normal;
    word-break: break-word;
    overflow-wrap: anywhere;
  }

  :deep(th) {
    background: rgba(56, 102, 245, 0.06);
    font-weight: var(--font-weight-semibold);
  }

  :deep(tr:nth-child(even) td) {
    background: rgba(15, 23, 42, 0.02);
  }
}
</style>
