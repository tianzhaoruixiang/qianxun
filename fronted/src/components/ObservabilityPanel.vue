<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getRunMetrics, listRuns, type RunMetrics, type RunSummary } from '@/api/runs'

const metrics = ref<RunMetrics | null>(null)
const runs = ref<RunSummary[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const [m, r] = await Promise.all([
      getRunMetrics(),
      listRuns(false, 30),
    ])
    metrics.value = m
    runs.value = r
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

defineExpose({ refresh })
</script>

<template>
  <div class="obs-panel">
    <header class="obs-head">
      <h3>运行观测</h3>
      <button type="button" class="link-btn" :disabled="loading" @click="refresh">刷新</button>
    </header>

    <div v-if="metrics" class="metrics-grid">
      <div class="metric-card">
        <div class="label">进行中 Run</div>
        <div class="value">{{ metrics.runningCount }}</div>
      </div>
      <div class="metric-card">
        <div class="label">跟踪中</div>
        <div class="value">{{ metrics.totalTracked }}</div>
      </div>
      <div class="metric-card">
        <div class="label">服务运行</div>
        <div class="value">{{ Math.round(metrics.uptimeMs / 60000) }}m</div>
      </div>
    </div>

    <section class="run-list">
      <h4>最近 Run</h4>
      <table v-if="runs.length">
        <thead>
          <tr>
            <th>状态</th>
            <th>会话</th>
            <th>Trace</th>
            <th>工具</th>
            <th>委派</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in runs" :key="r.runId">
            <td><span class="pill" :class="r.status.toLowerCase()">{{ r.status }}</span></td>
            <td class="mono" :title="r.sessionId">{{ r.sessionId.slice(0, 8) }}…</td>
            <td class="mono" :title="r.traceId || ''">{{ (r.traceId || '—').slice(0, 8) }}</td>
            <td>{{ r.toolCallCount ?? 0 }}</td>
            <td>{{ r.delegationCount ?? 0 }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty">暂无 Run 记录</p>
    </section>

    <p class="hint">Prometheus 指标：<code>/prometheus</code> · OpenAPI：<code>/QianXunService/swagger-ui.html</code></p>
  </div>
</template>

<style scoped>
.obs-panel { padding: 12px 0; }
.obs-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.obs-head h3 { margin: 0; font-size: 15px; }
.link-btn { border: none; background: none; color: #1677ff; cursor: pointer; }
.metrics-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 16px; }
.metric-card { background: #fafafa; border-radius: 8px; padding: 10px; }
.metric-card .label { font-size: 12px; color: #666; }
.metric-card .value { font-size: 20px; font-weight: 600; }
.run-list h4 { margin: 0 0 8px; font-size: 13px; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th, td { text-align: left; padding: 6px 4px; border-bottom: 1px solid #f0f0f0; }
.mono { font-family: ui-monospace, monospace; }
.pill {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 999px;
  background: #f5f5f5;
  font-size: 11px;
}
.pill.running { background: #e6f4ff; color: #1677ff; }
.pill.completed { background: #f6ffed; color: #389e0d; }
.pill.failed, .pill.cancelled { background: #fff2f0; color: #cf1322; }
.empty { color: #999; font-size: 13px; }
.hint { margin-top: 12px; font-size: 11px; color: #999; }
</style>
