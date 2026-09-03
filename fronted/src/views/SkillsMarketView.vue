<template>
  <div class="skills-view">
    <header class="skills-header">
      <div class="titles">
        <h1>技能市场</h1>
        <p class="sub">{{ brandCopy.marketSkillsSub }}</p>
      </div>
      <div class="actions">
        <a-select
          v-model:value="profile"
          show-search
          allow-clear
          placeholder="选择专业智能体"
          class="profile-sel"
          :options="profileOptions"
          :loading="profilesLoading"
          option-filter-prop="label"
        />
        <a-button @click="loadSkills">
          <template #icon><AppGlyph name="reload" size="sm" /></template>
          刷新
        </a-button>
        <a-upload :show-upload-list="false" accept=".zip" :before-upload="onUploadZip">
          <a-button type="primary" :loading="uploading" :disabled="!profile">
            <template #icon><AppGlyph name="upload" size="sm" /></template>
            上传 zip
          </a-button>
        </a-upload>
      </div>
    </header>

    <MarketSectionTabs section="skills" />

    <a-input
      v-model:value="keyword"
      allow-clear
      placeholder="按名称、描述、分类搜索"
      class="search-bar"
    >
      <template #prefix><AppGlyph name="search" size="sm" /></template>
    </a-input>

    <div class="workspace">
      <section class="list-pane">
        <a-spin :spinning="loading">
          <div v-if="filteredSkills.length" class="skill-list">
            <article
              v-for="s in filteredSkills"
              :key="s.name"
              class="skill-card"
              :class="{ active: selected?.name === s.name, disabled: !s.enabled }"
              @click="openSkill(s)"
            >
              <div class="card-top">
                <span class="skill-glyph"><AppGlyph :name="skillGlyph(s)" size="md" /></span>
                <div class="skill-meta">
                  <h2>{{ s.name }}</h2>
                  <span class="state-text" :class="{ on: s.enabled }">{{ s.enabled ? '已启用' : '已停用' }}</span>
                </div>
                <a-switch
                  size="small"
                  :checked="s.enabled"
                  @click.stop
                  @change="(v: boolean) => onToggle(s, v)"
                />
              </div>
              <p class="desc">{{ s.description || '暂无描述' }}</p>
              <div class="facts">
                <a-tag v-if="s.category">{{ s.category }}</a-tag>
                <span v-if="s.provenance">{{ provenanceLabel(s.provenance) }}</span>
              </div>
            </article>
          </div>
          <a-empty v-else :description="emptyHint" class="empty">
            <template #image>
              <AppGlyph name="skill" size="xl" class="empty-glyph" />
            </template>
          </a-empty>
        </a-spin>
      </section>

      <section class="detail-pane">
        <template v-if="selected">
          <div class="detail-head">
            <div>
              <h2>{{ selected.name }}</h2>
              <p class="path-hint">{{ currentFilePath || 'SKILL.md' }}</p>
            </div>
            <div class="detail-actions">
              <a-button size="small" @click="downloadSelected" :loading="downloading">下载 zip</a-button>
              <a-button size="small" type="primary" :disabled="!canSave" :loading="saving" @click="saveCurrent">
                保存
              </a-button>
            </div>
          </div>
          <div class="detail-body">
            <div class="file-tree">
              <button
                v-for="n in fileNodes"
                :key="n.path"
                type="button"
                class="file-row"
                :class="{ dir: n.directory, current: currentFilePath === n.path }"
                :style="{ paddingLeft: `${8 + depthOf(n.path) * 12}px` }"
                :disabled="n.directory"
                @click="n.directory ? undefined : openFile(n)"
              >
                <span class="file-glyph">
                  <FileTypeIcon :kind="fileKind(n)" />
                </span>
                <span class="file-name">{{ n.name }}</span>
              </button>
            </div>
            <div class="editor">
              <a-spin :spinning="fileLoading">
                <a-textarea
                  v-if="currentText"
                  v-model:value="editorContent"
                  class="editor-area"
                  :disabled="!currentText"
                />
                <a-empty v-else description="二进制文件不可在线编辑，请下载技能包查看">
                  <template #image>
                    <AppGlyph name="zip" size="xl" class="empty-glyph" />
                  </template>
                </a-empty>
              </a-spin>
            </div>
          </div>
        </template>
        <a-empty v-else description="选择左侧技能以浏览和修改" class="empty">
          <template #image>
            <AppGlyph name="skill" size="xl" class="empty-glyph" />
          </template>
        </a-empty>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import MarketSectionTabs from '@/components/MarketSectionTabs.vue'
import FileTypeIcon from '@/components/FileTypeIcon.vue'
import AppGlyph from '@/components/AppGlyph.vue'
import {
  downloadHermesSkillZip,
  listHermesSkillTree,
  listHermesSkills,
  readHermesSkillFile,
  toggleHermesSkill,
  updateHermesSkillFile,
  uploadHermesSkillZip,
  type HermesSkillFileNode,
  type HermesSkillItem,
} from '@/api/hermes'
import { listRegistryAgents, type AgentRegistryItem } from '@/api/registry'
import { useHermesProfileStore } from '@/stores/useHermesProfileStore'
import {
  UNCATEGORIZED_AGENT_NAME,
  displayNameForHermesProfile,
  isDefaultHermesProfile,
} from '@/utils/agentDisplay'
import { getSystemName } from '@/utils/systemName'
import { brandCopy } from '@/utils/brandCopy'

const profileStore = useHermesProfileStore()
const agents = ref<AgentRegistryItem[]>([])
const profilesLoading = ref(false)
const profile = ref('')
const keyword = ref('')
const loading = ref(false)
const uploading = ref(false)
const downloading = ref(false)
const saving = ref(false)
const fileLoading = ref(false)
const skills = ref<HermesSkillItem[]>([])
const selected = ref<HermesSkillItem | null>(null)
const fileNodes = ref<HermesSkillFileNode[]>([])
const currentFilePath = ref('SKILL.md')
const currentText = ref(true)
const editorContent = ref('')
const savedContent = ref('')

function profileDisplayName(name: string): string {
  const n = (name || '').trim()
  if (isDefaultHermesProfile(n)) return getSystemName()
  const fromAgent = displayNameForHermesProfile(n, agents.value)
  if (fromAgent && fromAgent !== UNCATEGORIZED_AGENT_NAME) return fromAgent
  return n
}

const profileOptions = computed(() => {
  const seen = new Set<string>()
  const opts: { label: string; value: string }[] = []
  for (const a of agents.value) {
    const p = (a.hermesProfile || '').trim()
    if (!p || seen.has(p)) continue
    seen.add(p)
    opts.push({ label: a.name, value: p })
  }
  for (const p of profileStore.profiles) {
    if (!p.name || seen.has(p.name)) continue
    seen.add(p.name)
    const label = profileDisplayName(p.name)
    opts.push({ label: p.active ? `${label}（当前）` : label, value: p.name })
  }
  return opts
})

const filteredSkills = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const list = k
    ? skills.value.filter((s) =>
        [s.name, s.description || '', s.category || '', s.provenance || ''].join(' ').toLowerCase().includes(k),
      )
    : skills.value.slice()
  return list.sort((a, b) => {
    if (a.enabled !== b.enabled) return a.enabled ? -1 : 1
    return (a.name || '').localeCompare(b.name || '', 'zh-CN')
  })
})

const canSave = computed(
  () => !!selected.value && currentText.value && editorContent.value !== savedContent.value,
)

const emptyHint = computed(() => {
  if (!profile.value) return '请先选择专业智能体'
  return '该专业智能体暂无技能，可上传 zip 技能包'
})

function provenanceLabel(p: string): string {
  const m: Record<string, string> = { hub: 'Hub', bundled: '内置', agent: '自定义' }
  return m[p] || p
}

/** 按技能分类给卡片换一套更贴切的图标 */
function skillGlyph(s: { name?: string; category?: string }): string {
  const key = `${s.category || ''} ${s.name || ''}`.toLowerCase()
  if (/creative|design|media|image|video|audio|art|写|绘|视频|音频/.test(key)) return 'image'
  if (/research|web|search|wiki|检索|研究|调研/.test(key)) return 'web'
  if (/software|dev|code|git|编程|开发|代码/.test(key)) return 'terminal'
  if (/mlops|ml|data|分析|模型/.test(key)) return 'analysis'
  if (/note|doc|write|文档|笔记|写作/.test(key)) return 'document'
  if (/productiv|todo|办公|效率/.test(key)) return 'grid'
  if (/social|email|chat|社交|邮件/.test(key)) return 'chat'
  if (/home|smart|iot|家居/.test(key)) return 'market'
  if (/agent|autonomous|智能体/.test(key)) return 'agent'
  return 'skill'
}

function fileKind(n: HermesSkillFileNode): string {
  if (n.directory) return 'folder'
  if (!n.text) return 'archive'
  const ext = n.name.includes('.') ? n.name.split('.').pop() || 'file' : 'text'
  return ext.toLowerCase()
}

function depthOf(path: string): number {
  return path.split('/').length - 1
}

async function loadProfiles() {
  profilesLoading.value = true
  try {
    const [agentList] = await Promise.all([
      listRegistryAgents(false).catch(() => [] as AgentRegistryItem[]),
      profileStore.refresh(false),
    ])
    agents.value = agentList
    if (!profile.value) {
      profile.value =
        profileStore.selectedProfile
        || profileStore.profiles[0]?.name
        || agentList.find((a) => a.hermesProfile)?.hermesProfile?.trim()
        || ''
    }
  } finally {
    profilesLoading.value = false
  }
}

async function loadSkills() {
  if (!profile.value) {
    skills.value = []
    selected.value = null
    return
  }
  loading.value = true
  try {
    skills.value = await listHermesSkills(profile.value)
    if (selected.value && !skills.value.some((s) => s.name === selected.value?.name)) {
      selected.value = null
      fileNodes.value = []
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载技能失败')
    skills.value = []
  } finally {
    loading.value = false
  }
}

async function openSkill(s: HermesSkillItem) {
  selected.value = s
  fileLoading.value = true
  try {
    fileNodes.value = await listHermesSkillTree(profile.value, s.name)
    const skillMd = fileNodes.value.find((n) => !n.directory && n.name.toLowerCase() === 'skill.md')
    await openFile(skillMd || { path: 'SKILL.md', name: 'SKILL.md', directory: false, text: true })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '读取技能文件失败')
    fileNodes.value = [{ path: 'SKILL.md', name: 'SKILL.md', directory: false, text: true }]
    await openFile(fileNodes.value[0])
  } finally {
    fileLoading.value = false
  }
}

async function openFile(n: HermesSkillFileNode) {
  if (n.directory) return
  fileLoading.value = true
  currentFilePath.value = n.path
  try {
    const file = await readHermesSkillFile(profile.value, selected.value!.name, n.path)
    currentText.value = file.text
    editorContent.value = file.content || ''
    savedContent.value = editorContent.value
  } catch (e) {
    currentText.value = n.text
    editorContent.value = ''
    savedContent.value = ''
    message.error(e instanceof Error ? e.message : '读取文件失败')
  } finally {
    fileLoading.value = false
  }
}

async function saveCurrent() {
  if (!selected.value || !currentText.value) return
  saving.value = true
  try {
    await updateHermesSkillFile(profile.value, selected.value.name, currentFilePath.value, editorContent.value)
    savedContent.value = editorContent.value
    message.success('已保存')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function onToggle(s: HermesSkillItem, enabled: boolean) {
  try {
    await toggleHermesSkill(profile.value, s.name, enabled)
    s.enabled = enabled
  } catch (e) {
    message.error(e instanceof Error ? e.message : '切换失败')
  }
}

async function onUploadZip(file: File) {
  if (!profile.value) {
    message.warning('请先选择专业智能体')
    return false
  }
  uploading.value = true
  try {
    const r = await uploadHermesSkillZip(profile.value, file)
    const extra = r.errors?.length ? `（部分失败：${r.errors.join('; ')}）` : ''
    message.success(`已安装 ${r.installed?.join('、') || '技能'}${extra}`)
    await loadSkills()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

async function downloadSelected() {
  if (!selected.value) return
  downloading.value = true
  try {
    await downloadHermesSkillZip(profile.value, selected.value.name)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载失败')
  } finally {
    downloading.value = false
  }
}

watch(profile, () => {
  selected.value = null
  fileNodes.value = []
  void loadSkills()
})

onMounted(async () => {
  await loadProfiles()
  await loadSkills()
})
</script>

<style scoped lang="scss">
@import '@/styles/mixins.scss';

.skills-view {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 28px;
  overflow: hidden;
  background: var(--bg-base);
}

.skills-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;

  .titles h1 {
    margin: 0 0 6px;
    font-size: var(--font-size-xl);
    font-weight: var(--font-weight-semibold);
    color: var(--text-primary);
  }

  .sub {
    margin: 0;
    font-size: var(--font-size-sm);
    color: var(--text-muted);
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
  }
}

.profile-sel {
  min-width: 220px;
}

.search-bar {
  max-width: 420px;
  margin-bottom: 16px;
}

.workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(260px, 360px) 1fr;
  gap: 16px;
}

.list-pane,
.detail-pane {
  min-height: 0;
  overflow: auto;
  background: var(--card-bg);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.list-pane {
  padding: 12px;
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skill-card {
  @include market-card;
  @include market-disabled;
  padding: 16px;
  cursor: pointer;

  &.active {
    border-color: var(--card-border-hover, rgba(0, 0, 0, 0.1));
    box-shadow: var(--card-shadow-hover);
    background: var(--card-bg-solid, #fff);
  }

  h2 {
    margin: 0;
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    letter-spacing: var(--letter-spacing-tight);
    color: var(--text-primary);
  }

  .card-top {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    align-items: center;
  }

  .skill-glyph {
    width: 36px;
    height: 36px;
    border-radius: 11px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(118, 118, 128, 0.1);
    flex-shrink: 0;
  }

  .skill-meta {
    flex: 1;
    min-width: 0;
  }

  .state-text {
    display: block;
    margin-top: 2px;
    font-size: var(--font-size-xs);
    color: var(--text-muted);

    &.on {
      color: var(--color-accent);
    }
  }

  .desc {
    margin: 6px 0 0;
    font-size: var(--font-size-xs);
    color: var(--text-secondary);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .facts {
    margin-top: 8px;
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: var(--font-size-xs);
    color: var(--text-muted);
  }
}

.detail-pane {
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  h2 {
    margin: 0;
    font-size: var(--font-size-lg);
  }

  .path-hint {
    margin: 4px 0 0;
    font-size: var(--font-size-xs);
    color: var(--text-muted);
  }
}

.detail-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.detail-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 12px;
}

.file-tree {
  overflow: auto;
  border-right: 1px solid var(--border-subtle);
  padding-right: 8px;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  padding: 5px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover:not(.dir) {
    background: rgba(59, 130, 246, 0.08);
  }

  &.dir {
    cursor: default;
    color: var(--text-muted);
  }

  &.current {
    background: var(--bg-elevated);
    color: var(--text-primary);
    font-weight: var(--font-weight-semibold);
  }

  .file-glyph {
    flex: none;
    line-height: 0;

    :deep(.ft-icon) {
      width: 18px;
      height: 18px;
      filter: none;
    }
  }

  .file-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.editor {
  min-width: 0;
  min-height: 0;
}

.editor-area {
  height: calc(100vh - 280px);
  min-height: 320px;
  font-family: var(--font-family-mono);
}

.empty {
  margin: 48px 0;

  .empty-glyph {
    width: 42px;
    height: 42px;
  }
}
</style>
