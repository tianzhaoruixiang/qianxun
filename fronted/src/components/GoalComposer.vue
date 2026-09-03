<template>
  <div class="goal-composer" role="dialog" aria-label="设定长程目标">
    <div class="goal-composer-head">
      <AppGlyph name="goal" size="sm" />
      <span>长程目标</span>
    </div>
    <p class="goal-hint">
      对应智能体 <code>/goal</code>：只提交一条可验证的完成条件。设定后会立即开一轮，之后由评估器判断是否继续，无需再发提示词。
    </p>
    <label class="goal-field">
      <span>完成条件</span>
      <textarea
        ref="conditionRef"
        v-model="description"
        rows="4"
        maxlength="4000"
        placeholder="例如：test/auth 下全部测试通过且 lint 干净"
        @keydown="onKeydown"
      />
    </label>
    <label class="goal-field">
      <span>简要名称 <em>可选，仅会话条展示</em></span>
      <input
        v-model="title"
        type="text"
        maxlength="120"
        placeholder="不填则用完成条件的前几十字"
        @keydown="onKeydown"
      />
    </label>
    <label class="goal-field">
      <span>验收方式 <em>可选</em></span>
      <textarea
        v-model="steps"
        rows="2"
        maxlength="800"
        placeholder="例如：运行测试并把退出码写进对话（评估器不另跑命令）"
        @keydown="onKeydown"
      />
    </label>
    <label class="goal-field">
      <span>约束 <em>可选</em></span>
      <textarea
        v-model="constraints"
        rows="2"
        maxlength="800"
        placeholder="例如：不修改其它测试文件"
        @keydown="onKeydown"
      />
    </label>
    <label class="goal-field goal-field-inline">
      <span>轮次上限 <em>可选</em></span>
      <input
        v-model.number="stopAfterTurns"
        type="number"
        min="1"
        max="200"
        placeholder="如 20"
        @keydown="onKeydown"
      />
    </label>
    <p class="goal-preview" :title="preview">将下发：{{ preview }}</p>
    <div class="goal-actions">
      <button type="button" class="goal-btn ghost" @click="$emit('cancel')">取消</button>
      <button type="button" class="goal-btn primary" :disabled="!canConfirm" @click="confirm">
        确认并开始
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import AppGlyph from '@/components/AppGlyph.vue'
import { formatGoalCondition, type SessionGoal } from '@/utils/sessionGoal'

const props = defineProps<{
  initial?: SessionGoal | null
  draftTitle?: string
}>()

const emit = defineEmits<{
  confirm: [goal: SessionGoal]
  cancel: []
}>()

const conditionRef = ref<HTMLTextAreaElement | null>(null)
const title = ref(props.initial?.title || '')
const description = ref(props.draftTitle || props.initial?.description || '')
const steps = ref(props.initial?.steps || '')
const constraints = ref(props.initial?.constraints || '')
const stopAfterTurns = ref<number | ''>(props.initial?.stopAfterTurns || '')

const draftGoal = computed<SessionGoal>(() => ({
  title: title.value.trim(),
  description: description.value.trim(),
  steps: steps.value.trim(),
  constraints: constraints.value.trim(),
  stopAfterTurns: normalizeTurns(stopAfterTurns.value),
}))

const canConfirm = computed(() => !!(draftGoal.value.description || draftGoal.value.title))

const preview = computed(() => {
  if (!canConfirm.value) return '/goal …'
  const cond = formatGoalCondition(draftGoal.value)
  return `/goal ${cond.length > 160 ? `${cond.slice(0, 160)}…` : cond}`
})

onMounted(() => {
  void nextTick(() => conditionRef.value?.focus())
})

function normalizeTurns(raw: number | '' | null | undefined): number | null {
  const n = typeof raw === 'number' ? raw : Number(raw)
  if (!Number.isFinite(n) || n <= 0) return null
  return Math.min(200, Math.round(n))
}

function confirm() {
  if (!canConfirm.value) return
  const g = draftGoal.value
  emit('confirm', {
    title: g.title || (g.description || '').slice(0, 40),
    description: g.description || g.title,
    steps: g.steps,
    constraints: g.constraints,
    stopAfterTurns: g.stopAfterTurns,
  })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault()
    emit('cancel')
  }
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && canConfirm.value) {
    e.preventDefault()
    confirm()
  }
}
</script>

<style scoped lang="scss">
.goal-composer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 4px 4px;
}

.goal-composer-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  line-height: 1.25;

  .ag-icon {
    flex-shrink: 0;
  }
}

.goal-hint,
.goal-preview {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  line-height: 1.45;
}

.goal-preview {
  font-family: var(--font-family-mono, ui-monospace, monospace);
  word-break: break-all;
}

.goal-hint code {
  font-size: inherit;
}

.goal-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);

  em {
    font-style: normal;
    opacity: 0.7;
  }

  input,
  textarea {
    width: 100%;
    border: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
    border-radius: 6px;
    background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
    padding: 6px 10px;
    font-size: var(--font-size-md);
    color: var(--text-primary);
    font-family: var(--font-family-base);
    min-height: 32px;
    box-sizing: border-box;
    resize: vertical;

    &:focus {
      outline: none;
      border-color: var(--color-primary-light);
    }
  }
}

.goal-field-inline input {
  max-width: 120px;
}

.goal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

.goal-btn {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 6px;
  font-size: var(--font-size-sm);
  cursor: pointer;

  &.ghost {
    border: 1px solid var(--chat-glass-border, rgba(15, 23, 42, 0.06));
    background: var(--chat-chip-bg, rgba(255, 255, 255, 0.58));
    color: var(--text-primary);
  }

  &.primary {
    border: none;
    background: linear-gradient(55deg, #3e72d8 -15%, #36a6fe 94%);
    color: #fff;

    &:disabled {
      background: #d1d5db;
      cursor: not-allowed;
    }
  }
}
</style>
