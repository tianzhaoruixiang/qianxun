<template>
  <a-modal
    :open="open"
    title="系统设置"
    :confirm-loading="saving"
    destroy-on-close
    ok-text="保存"
    @ok="onOk"
    @cancel="$emit('update:open', false)"
  >
    <p class="hint">仅管理员可改。对话走 LiteLLM，下列三项是标准 OpenAI Compatible 上游（厂商 / 内网 /v1），不是网关别名 openai-default。</p>
    <a-form layout="vertical">
      <a-form-item label="系统名称" required>
        <a-input v-model:value="form.systemName" :maxlength="32" show-count :placeholder="`例如：${DEFAULT_BRAND_NAME}`" />
      </a-form-item>
      <a-form-item label="Base URL" required>
        <a-input
          v-model:value="form.openaiBaseUrl"
          placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
        />
        <span class="hint">须含协议，一般以 /v1 结尾。本机服务请用 host.docker.internal，不要用 127.0.0.1。</span>
      </a-form-item>
      <a-form-item :label="apiKeyLabel">
        <a-input-password
          v-model:value="form.openaiApiKey"
          :placeholder="apiKeyPlaceholder"
          autocomplete="new-password"
        />
        <span class="hint">{{ apiKeyHint }}</span>
      </a-form-item>
      <a-form-item label="上游模型" required>
        <div class="model-row">
          <a-select
            v-model:value="form.claudeChatModel"
            show-search
            allow-clear
            :options="modelOptions"
            :loading="modelsLoading"
            :filter-option="filterOption"
            @search="onModelSearch"
            placeholder="按 Base URL 拉取后选择，也可搜索或输入 id"
            style="flex: 1"
          />
          <a-button :loading="modelsLoading" @click="loadUpstreamModels">拉取模型</a-button>
        </div>
        <span class="hint">{{ modelsHint }}</span>
      </a-form-item>
      <a-divider style="margin: 8px 0 16px" />
      <p class="hint">记忆系统（Mem0）嵌入模型：保存后会热更新到本地 Mem0。改维数会换新 collection，旧记忆不自动迁移。</p>
      <a-form-item label="记忆嵌入模型">
        <a-select
          v-model:value="form.mem0EmbedderModel"
          show-search
          allow-clear
          :options="embedderOptions"
          :filter-option="filterOption"
          @change="onEmbedderChange"
          @search="onEmbedderSearch"
          placeholder="例如 text-embedding-v3"
        />
      </a-form-item>
      <a-form-item label="嵌入向量维数">
        <a-input-number
          v-model:value="form.mem0EmbeddingDims"
          :min="64"
          :max="8192"
          :step="64"
          style="width: 100%"
        />
        <span class="hint">百炼 text-embedding-v3 常用 1024；OpenAI text-embedding-3-small 为 1536。</span>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { fetchSystemSettings, fetchUpstreamModels, updateSystemSettings } from '@/api/welcome'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { DEFAULT_BRAND_NAME } from '@/utils/brandCopy'
import { formatTokenCount } from '@/utils/contextUsage'

const EMBEDDER_PRESETS = [
  'text-embedding-v3',
  'text-embedding-v2',
  'text-embedding-3-small',
  'text-embedding-3-large',
]

function suggestDims(model: string, fallback: number): number {
  const m = String(model || '').trim().toLowerCase()
  if (m.includes('text-embedding-3-large')) return 3072
  if (m.includes('text-embedding-3-small') || m.includes('text-embedding-ada') || m === 'text-embedding-v2') return 1536
  if (m.includes('text-embedding-v3') || m.includes('text-embedding-v4')) return 1024
  return fallback > 0 ? fallback : 1024
}

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [boolean] }>()

const bootstrap = useBootstrapStore()
const saving = ref(false)
const modelsLoading = ref(false)
const upstreamModels = ref<string[]>([])
const modelWindows = ref<Record<string, number>>({})
const typedModels = ref<string[]>([])
const typedEmbedders = ref<string[]>([])
const modelsError = ref('')
const keyConfigured = ref(false)
const keyMasked = ref('')
const form = reactive({
  systemName: '',
  claudeChatModel: '',
  openaiBaseUrl: '',
  openaiApiKey: '',
  mem0EmbedderModel: 'text-embedding-v3',
  mem0EmbeddingDims: 1024 as number,
})

const modelOptions = computed(() => {
  const seen = new Set<string>()
  const opts: { label: string; value: string }[] = []
  const push = (id: string) => {
    const v = id.trim()
    if (!v || seen.has(v)) return
    seen.add(v)
    const w = modelWindows.value[v]
    opts.push({ label: w > 0 ? `${v}（${formatTokenCount(w)}）` : v, value: v })
  }
  push(form.claudeChatModel)
  for (const id of typedModels.value) {
    push(id)
  }
  for (const id of upstreamModels.value) {
    push(id)
  }
  return opts
})

const embedderOptions = computed(() => {
  const seen = new Set<string>()
  const opts: { label: string; value: string }[] = []
  const push = (id: string) => {
    const v = id.trim()
    if (!v || seen.has(v)) return
    seen.add(v)
    opts.push({ label: v, value: v })
  }
  push(form.mem0EmbedderModel)
  for (const id of typedEmbedders.value) push(id)
  for (const id of EMBEDDER_PRESETS) push(id)
  for (const id of upstreamModels.value) {
    if (/embed/i.test(id)) push(id)
  }
  return opts
})

const modelsHint = computed(() => {
  if (modelsLoading.value) return '正在查询上游 GET /models …'
  if (modelsError.value) return modelsError.value
  if (upstreamModels.value.length) return `已列出 ${upstreamModels.value.length} 个上游模型，可搜索 qwen3-plus。`
  return '填写 Base URL 后点击「拉取模型」。已保存密钥时不必再填 API Key。'
})

const apiKeyLabel = computed(() => (keyConfigured.value ? 'API Key（已配置）' : 'API Key'))
const apiKeyPlaceholder = computed(() =>
  keyConfigured.value ? '留空则不修改已保存的密钥' : 'OpenAI Compatible API Key',
)
const apiKeyHint = computed(() => {
  if (keyMasked.value) return `当前：${keyMasked.value}`
  return '只给 LiteLLM 调上游使用，不会出现在普通用户界面。'
})

function onModelSearch(input: string) {
  const t = input.trim()
  if (!t) return
  typedModels.value = [t, ...typedModels.value.filter((x) => x !== t)].slice(0, 12)
}

function onEmbedderSearch(input: string) {
  const t = input.trim()
  if (!t) return
  typedEmbedders.value = [t, ...typedEmbedders.value.filter((x) => x !== t)].slice(0, 12)
}

function onEmbedderChange(value: string) {
  const v = String(value || '').trim()
  if (!v) return
  form.mem0EmbeddingDims = suggestDims(v, form.mem0EmbeddingDims || 1024)
}

function filterOption(input: string, option: { label?: string; value?: string }) {
  const q = input.trim().toLowerCase()
  if (!q) return true
  return `${option.label || ''} ${option.value || ''}`.toLowerCase().includes(q)
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    form.systemName = bootstrap.systemName
    form.claudeChatModel = bootstrap.claudeChatModel
    form.openaiBaseUrl = ''
    form.openaiApiKey = ''
    form.mem0EmbedderModel = 'text-embedding-v3'
    form.mem0EmbeddingDims = 1024
    upstreamModels.value = []
    typedModels.value = []
    typedEmbedders.value = []
    modelWindows.value = {}
    modelsError.value = ''
    keyConfigured.value = false
    keyMasked.value = ''
    void loadSettings()
  },
)

async function loadSettings() {
  try {
    const saved = await fetchSystemSettings()
    form.systemName = saved.systemName || form.systemName
    form.claudeChatModel = saved.claudeChatModel || form.claudeChatModel
    form.openaiBaseUrl = saved.openaiBaseUrl || ''
    form.openaiApiKey = ''
    form.mem0EmbedderModel = saved.mem0EmbedderModel || form.mem0EmbedderModel
    form.mem0EmbeddingDims = saved.mem0EmbeddingDims && saved.mem0EmbeddingDims > 0
      ? saved.mem0EmbeddingDims
      : suggestDims(form.mem0EmbedderModel, 1024)
    keyConfigured.value = !!saved.openaiApiKeyConfigured
    keyMasked.value = saved.openaiApiKeyMasked || ''
    bootstrap.applySystemChrome(saved.systemName)
    bootstrap.claudeChatModel = saved.claudeChatModel
    bootstrap.claudeChatContextWindow = saved.claudeChatContextWindow && saved.claudeChatContextWindow > 0
      ? saved.claudeChatContextWindow
      : 0
    if (form.openaiBaseUrl) {
      await loadUpstreamModels()
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载系统设置失败')
  }
}

async function loadUpstreamModels() {
  const openaiBaseUrl = form.openaiBaseUrl.trim()
  if (!openaiBaseUrl) {
    message.warning('请先填写 Base URL')
    return
  }
  modelsLoading.value = true
  modelsError.value = ''
  try {
    const data = await fetchUpstreamModels({
      openaiBaseUrl,
      openaiApiKey: form.openaiApiKey.trim(),
    })
    upstreamModels.value = Array.isArray(data?.models) ? data.models : []
    const next: Record<string, number> = {}
    for (const item of data?.items || []) {
      if (item?.id && item.contextWindow && item.contextWindow > 0) {
        next[item.id] = item.contextWindow
      }
    }
    modelWindows.value = next
    if (!upstreamModels.value.length) {
      modelsError.value = '上游未返回模型，可直接输入模型 id'
    }
  } catch (e) {
    upstreamModels.value = []
    modelsError.value = e instanceof Error ? e.message : '查询上游模型失败'
  } finally {
    modelsLoading.value = false
  }
}

async function onOk() {
  const systemName = form.systemName.trim()
  const claudeChatModel = String(form.claudeChatModel || '').trim()
  const openaiBaseUrl = form.openaiBaseUrl.trim()
  const openaiApiKey = form.openaiApiKey.trim()
  const mem0EmbedderModel = String(form.mem0EmbedderModel || '').trim()
  const mem0EmbeddingDims = Number(form.mem0EmbeddingDims) || 0
  if (!systemName) {
    message.warning('请填写系统名称')
    return
  }
  if (!claudeChatModel) {
    message.warning('请填写上游模型')
    return
  }
  if (!openaiBaseUrl) {
    message.warning('请填写 Base URL')
    return
  }
  if (!mem0EmbedderModel) {
    message.warning('请填写记忆嵌入模型')
    return
  }
  if (mem0EmbeddingDims < 64 || mem0EmbeddingDims > 8192) {
    message.warning('嵌入维数须在 64–8192')
    return
  }
  saving.value = true
  try {
    const saved = await updateSystemSettings({
      systemName,
      claudeChatModel,
      openaiBaseUrl,
      openaiApiKey,
      mem0EmbedderModel,
      mem0EmbeddingDims,
    })
    bootstrap.applySystemChrome(saved.systemName)
    bootstrap.claudeChatModel = saved.claudeChatModel
    bootstrap.claudeChatContextWindow = saved.claudeChatContextWindow && saved.claudeChatContextWindow > 0
      ? saved.claudeChatContextWindow
      : (modelWindows.value[saved.claudeChatModel] || 0)
    if (saved.mem0ApplyWarning) {
      message.warning(`已保存设置，但 ${saved.mem0ApplyWarning}`)
    } else {
      message.success('已保存系统设置')
    }
    emit('update:open', false)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.hint {
  margin: 4px 0 0;
  color: var(--text-muted, #64748b);
  font-size: 13px;
  line-height: 1.5;
}

.hint:first-child {
  margin: 0 0 12px;
}

.model-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
