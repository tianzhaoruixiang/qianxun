<template>
  <div class="tool-detail-body-root">
    <div v-if="tool.risk || tool.redacted || tool.findings?.length" class="tool-detail-block">
      <div class="tool-detail-label">安全提示</div>
      <div class="tool-risk-card">
        <div class="tool-risk-tags">
          <span v-if="tool.risk" class="tool-risk-badge" :class="riskLevelClass(tool.risk)">
            {{ riskLevelLabel(tool.risk) }}
          </span>
          <span v-if="tool.redacted" class="tool-risk-badge muted">已脱敏</span>
        </div>
        <ul v-if="tool.findings?.length" class="tool-finding-list">
          <li v-for="(f, fi) in tool.findings" :key="fi">{{ f }}</li>
        </ul>
      </div>
    </div>

    <div class="tool-detail-block">
      <div class="tool-detail-label">调用信息</div>
      <dl v-if="detail.argRows.length" class="tool-kv-list">
        <div v-for="(row, ri) in detail.argRows" :key="`${row.label}-${ri}`" class="tool-kv-row">
          <dt>{{ row.label }}</dt>
          <dd :class="{ mono: row.mono, multiline: row.multiline }">{{ row.value }}</dd>
        </div>
      </dl>
      <p v-else class="tool-detail-empty">本次调用未附带额外输入</p>
    </div>

    <div v-if="isLive && !detail.resultSections.length" class="tool-detail-block">
      <div class="tool-detail-label">执行结果</div>
      <p class="tool-detail-empty">正在执行，完成后将在此展示结果</p>
    </div>

    <div
      v-for="(section, si) in detail.resultSections"
      :key="`sec-${si}`"
      class="tool-detail-block"
    >
      <div v-if="sectionTitle(section)" class="tool-detail-label">
        {{ sectionTitle(section) }}
      </div>

      <div v-if="section.kind === 'status'" class="tool-status-chips">
        <span
          v-for="(chip, ci) in section.items"
          :key="ci"
          class="tool-status-chip"
          :class="chip.tone || 'muted'"
        >{{ chip.label }}</span>
      </div>

      <dl v-else-if="section.kind === 'kv'" class="tool-kv-list">
        <div v-for="(row, ri) in section.rows" :key="`${row.label}-${ri}`" class="tool-kv-row">
          <dt>{{ row.label }}</dt>
          <dd :class="{ mono: row.mono, multiline: row.multiline }">{{ row.value }}</dd>
        </div>
      </dl>

      <div v-else-if="section.kind === 'cards'" class="tool-card-list">
        <article v-for="(card, ci) in section.items" :key="ci" class="tool-result-card">
          <a
            v-if="card.url"
            class="tool-card-title link"
            :href="card.url"
            target="_blank"
            rel="noopener noreferrer"
          >{{ card.title }}</a>
          <div v-else class="tool-card-title">{{ card.title }}</div>
          <div v-if="card.subtitle" class="tool-card-sub">{{ card.subtitle }}</div>
          <div v-if="card.url" class="tool-card-url">{{ card.url }}</div>
          <p v-if="card.description" class="tool-card-desc">{{ card.description }}</p>
        </article>
      </div>

      <ul v-else-if="section.kind === 'list'" class="tool-result-list">
        <li v-for="(item, ii) in section.items" :key="ii">{{ item }}</li>
      </ul>

      <div
        v-else-if="section.kind === 'text'"
        class="tool-detail-text"
        :class="{ mono: section.mono, error: section.error || isError }"
      >{{ section.text }}</div>
    </div>

    <div v-if="tool.stderr?.trim()" class="tool-detail-block">
      <div class="tool-detail-label">附加信息</div>
      <div class="tool-detail-text mono">{{ tool.stderr }}</div>
    </div>

    <div v-if="tool.inlineDiff?.trim()" class="tool-detail-block">
      <div class="tool-detail-label">变更内容</div>
      <div class="tool-detail-text mono diff">{{ tool.inlineDiff }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCallInfo } from '@/types/chat'
import {
  adaptToolDetail,
  isLiveToolStatus,
  toolCallFailure,
  type ToolDetailSection,
} from '@/utils/toolDetailAdapt'

const props = defineProps<{
  tool: ToolCallInfo
}>()

const detail = computed(() => adaptToolDetail(props.tool))

const isError = computed(() => !!toolCallFailure(props.tool))

const isLive = computed(() => isLiveToolStatus(props.tool.status))

function riskLevelLabel(risk: string): string {
  const r = risk.trim().toLowerCase()
  if (r === 'low' || r === 'info') return '低风险'
  if (r === 'medium' || r === 'warn' || r === 'warning') return '中风险'
  if (r === 'high' || r === 'critical' || r === 'danger') return '高风险'
  if (/风险|级/.test(risk)) return risk
  return `风险：${risk}`
}

function riskLevelClass(risk: string): string {
  const r = risk.trim().toLowerCase()
  if (r === 'low' || r === 'info') return 'low'
  if (r === 'medium' || r === 'warn' || r === 'warning') return 'mid'
  if (r === 'high' || r === 'critical' || r === 'danger') return 'high'
  return 'mid'
}

function sectionTitle(section: ToolDetailSection): string {
  if (section.title) return section.title
  if (section.kind === 'text' && (section.error || isError.value)) return '执行异常'
  if (section.kind === 'status') return '状态'
  if (section.kind === 'cards') return '结果'
  if (section.kind === 'list') return '列表'
  if (section.kind === 'kv') return '详情'
  return '执行结果'
}
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';

.tool-detail-body-root {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.tool-detail-block {
  min-width: 0;
  max-width: 100%;
}

.tool-detail-label {
  margin: 0 0 8px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--text-muted);
  letter-spacing: 0.02em;
}

.tool-detail-empty {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.08);
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.45;
}

.tool-risk-card {
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.tool-risk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tool-risk-badge {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  line-height: 1;

  &.low {
    color: #166534;
    background: rgba(34, 197, 94, 0.12);
  }

  &.mid {
    color: #b45309;
    background: rgba(245, 158, 11, 0.14);
  }

  &.high {
    color: #b91c1c;
    background: rgba(239, 68, 68, 0.12);
  }

  &.muted {
    color: var(--text-secondary);
    background: rgba(148, 163, 184, 0.14);
  }
}

.tool-finding-list {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.5;

  li + li {
    margin-top: 2px;
  }
}

.tool-kv-list {
  margin: 0;
  padding: 2px 0;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.tool-kv-row {
  display: grid;
  grid-template-columns: minmax(72px, 108px) minmax(0, 1fr);
  gap: 10px;
  padding: 8px 12px;
  align-items: start;

  & + & {
    border-top: 1px solid rgba(148, 163, 184, 0.12);
  }

  dt {
    margin: 0;
    padding-top: 1px;
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-medium);
    color: var(--text-muted);
    line-height: 1.45;
  }

  dd {
    margin: 0;
    min-width: 0;
    font-size: var(--font-size-sm);
    color: var(--text-primary);
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
    overflow-wrap: anywhere;

    &.mono {
      font-family: var(--font-family-mono);
      font-size: var(--font-size-xs);
      color: var(--text-secondary);
    }

    &.multiline {
      padding: 6px 8px;
      border-radius: 8px;
      background: rgba(15, 23, 42, 0.03);
    }
  }
}

.tool-status-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tool-status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  line-height: 1;

  &.ok {
    color: #166534;
    background: rgba(34, 197, 94, 0.12);
  }

  &.warn {
    color: #b45309;
    background: rgba(245, 158, 11, 0.14);
  }

  &.err {
    color: #b91c1c;
    background: rgba(239, 68, 68, 0.12);
  }

  &.muted {
    color: var(--text-secondary);
    background: rgba(148, 163, 184, 0.14);
  }
}

.tool-card-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-result-card {
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.tool-card-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  line-height: 1.4;
  word-break: break-word;

  &.link {
    color: var(--color-primary-dark, #2563eb);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.tool-card-sub,
.tool-card-url {
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  line-height: 1.4;
  word-break: break-all;
}

.tool-card-desc {
  margin: 6px 0 0;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: 1.5;
  word-break: break-word;
}

.tool-result-list {
  margin: 0;
  padding: 10px 12px 10px 28px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.16);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  line-height: 1.5;

  li + li {
    margin-top: 4px;
  }
}

.tool-detail-text {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-sizing: border-box;
  font-size: var(--font-size-sm);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  color: var(--text-secondary);
  max-height: min(52vh, 480px);
  overflow: auto;

  &.mono {
    font-family: var(--font-family-mono);
    font-size: var(--font-size-xs);
    background: rgba(15, 23, 42, 0.04);
  }

  &.error {
    color: #b91c1c;
    background: rgba(254, 226, 226, 0.55);
    border-color: rgba(248, 113, 113, 0.28);
  }

  &.diff {
    color: #334155;
  }
}
</style>
