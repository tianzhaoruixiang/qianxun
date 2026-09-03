<template>
  <div class="assistant-appendix" :class="{ collapsed, streaming: streamActive }">
    <button
      type="button"
      class="appendix-toggle"
      :class="{ collapsed, streaming: streamActive }"
      :aria-expanded="!collapsed"
      :aria-label="collapsed ? '展开附录' : '收起附录'"
      @click="collapsed = !collapsed"
    >
      <span v-if="streamActive" class="appendix-spinner" aria-hidden="true"></span>
      <span class="appendix-label">附录</span>
      <span v-if="streamActive" class="appendix-meta">生成中</span>
      <span class="appendix-chevron" aria-hidden="true">{{ collapsed ? '▸' : '▾' }}</span>
    </button>
    <div v-show="!collapsed" class="appendix-body">
      <MarkdownContent
        v-if="content"
        :preview-id="previewId"
        :content="content"
      />
      <p v-else-if="streamActive" class="appendix-placeholder">详细说明生成中…</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import MarkdownContent from '@/components/MarkdownContent.vue'

defineProps<{
  content: string
  previewId: string
  streamActive?: boolean
}>()

const collapsed = ref(true)
</script>

<style scoped lang="scss">
.assistant-appendix {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  max-width: 100%;
}

.appendix-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  min-height: 22px;
  margin: 0;
  padding: 0 2px;
  border: 0;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: var(--text-muted);
  transition: color 0.15s ease;

  &:hover {
    color: var(--text-secondary);
  }

  &.streaming {
    color: #2563eb;
  }
}

.appendix-spinner {
  flex-shrink: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: conic-gradient(
    from 180deg,
    rgba(56, 102, 245, 0) 0deg,
    rgba(54, 169, 255, 0.2) 80deg,
    #36a9ff 200deg,
    #3866f5 300deg,
    rgba(56, 102, 245, 0) 360deg
  );
  -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 1.9px));
  mask: radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 1.9px));
  animation: appendix-spin 0.85s linear infinite;
}

.appendix-label {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium, 500);
  line-height: 1.25;
}

.appendix-meta {
  font-size: var(--font-size-xs);
  line-height: 1.25;
  opacity: 0.78;
}

.appendix-chevron {
  flex-shrink: 0;
  font-size: 10px;
  opacity: 0.7;
  line-height: 1;
}

.appendix-body {
  width: 100%;
  min-width: 0;
}

.appendix-placeholder {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

@keyframes appendix-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
