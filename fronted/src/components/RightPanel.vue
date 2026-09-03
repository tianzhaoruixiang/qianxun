<template>
  <aside
    class="right-panel"
    :class="{ collapsed: layoutStore.rightPanelCollapsed }"
    :style="{ width: `${layoutStore.rightPanelWidth}px`, backgroundImage: `url(${rightBg})` }"
  >
    <!-- 收起态：右侧窄条，点击展开 -->
    <button
      v-if="layoutStore.rightPanelCollapsed"
      type="button"
      class="collapsed-rail"
      title="展开我的网盘"
      aria-label="展开我的网盘"
      @click="layoutStore.setRightPanelCollapsed(false)"
    >
      <img :src="unfoldIcon" alt="" class="rail-icon" />
      <span class="rail-text">我的网盘</span>
    </button>

    <div v-show="!layoutStore.rightPanelCollapsed" class="panel-content">
      <div class="panel-section">
        <div class="section-header">
          <span class="section-title"><img :src="subTitleIcon" alt="" class="section-icon" />我的网盘</span>
          <div class="section-actions">
            <input
              ref="uploadInputRef"
              type="file"
              class="hidden-file"
              multiple
              :accept="acceptAttr"
              @change="onUploadPicked"
            />
            <input
              ref="folderInputRef"
              type="file"
              class="hidden-file"
              multiple
              webkitdirectory
              directory
              @change="onUploadPicked"
            />
            <a-button
              type="text"
              size="small"
              class="action-btn"
              title="新建文件夹"
              @click="openFolderModal"
            >
              <AppGlyph name="folderAdd" size="lg" />
            </a-button>
            <a-dropdown :trigger="['click']" placement="bottomRight">
              <a-button
                type="text"
                size="small"
                class="action-btn"
                title="上传"
                :loading="uploading"
              >
                <AppGlyph name="upload" size="lg" />
              </a-button>
              <template #overlay>
                <a-menu @click="onUploadMenu">
                  <a-menu-item key="files">上传文件</a-menu-item>
                  <a-menu-item key="folder">上传文件夹</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <a-button
              type="text"
              size="small"
              class="action-btn"
              title="向右收起"
              aria-label="向右收起我的网盘"
              @click="layoutStore.setRightPanelCollapsed(true)"
            >
              <img :src="foldIcon" alt="" class="collapse-icon" />
            </a-button>
          </div>
        </div>

        <div v-if="uploading" class="upload-progress">
          <a-progress :percent="uploadPercent" size="small" :show-info="true" />
          <span class="upload-hint">{{ uploadHint }}</span>
        </div>

        <FileSearch />

        <FileList />

        <FileStats />
      </div>
    </div>

    <a-modal
      v-model:open="folderModalOpen"
      title="新建文件夹"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="creatingFolder"
      destroy-on-close
      @ok="submitFolder"
    >
      <p class="folder-hint">位置：{{ filesStore.currentPath || '根目录' }}</p>
      <a-input
        ref="folderNameRef"
        v-model:value="folderName"
        placeholder="文件夹名称"
        allow-clear
        @pressEnter="submitFolder"
      />
    </a-modal>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useLayoutStore } from '@/stores/useLayoutStore'
import { useDataFilesStore } from '@/stores/useDataFilesStore'
import { uploadAcceptAttr, uploadDataFiles } from '@/api/files'
import AppGlyph from '@/components/AppGlyph.vue'
import FileSearch from './FileSearch.vue'
import FileList from './FileList.vue'
import FileStats from './FileStats.vue'
import rightBg from '@/assets/images/rightBg.png'
import subTitleIcon from '@/assets/images/subTitleIcon.png'
import foldIcon from '@/assets/images/fold.svg'
import unfoldIcon from '@/assets/images/unfold.svg'

const layoutStore = useLayoutStore()
const filesStore = useDataFilesStore()
const uploadInputRef = ref<HTMLInputElement | null>(null)
const folderInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const uploadDone = ref(0)
const uploadTotal = ref(0)
const folderModalOpen = ref(false)
const folderName = ref('')
const creatingFolder = ref(false)
const folderNameRef = ref<{ focus?: () => void } | null>(null)
const acceptAttr = uploadAcceptAttr()

const uploadPercent = computed(() => {
  if (!uploadTotal.value) return 0
  return Math.min(100, Math.round((uploadDone.value / uploadTotal.value) * 100))
})

const uploadHint = computed(() => {
  if (!uploading.value) return ''
  return `正在上传 ${uploadDone.value}/${uploadTotal.value}`
})

function onUploadMenu(info: { key: string | number }) {
  if (uploading.value) return
  if (String(info.key) === 'folder') {
    const el = folderInputRef.value
    if (!el) return
    el.value = ''
    el.click()
    return
  }
  const el = uploadInputRef.value
  if (!el) return
  el.value = ''
  el.click()
}

function openFolderModal() {
  folderName.value = ''
  folderModalOpen.value = true
  void nextTick(() => folderNameRef.value?.focus?.())
}

async function submitFolder() {
  const name = folderName.value.trim()
  if (!name) {
    message.warning('请输入文件夹名称')
    return
  }
  creatingFolder.value = true
  try {
    await filesStore.createFolder(name)
    folderModalOpen.value = false
    message.success('已创建文件夹')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    creatingFolder.value = false
  }
}

async function onUploadPicked(ev: Event) {
  const input = ev.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length) return
  uploading.value = true
  uploadDone.value = 0
  uploadTotal.value = files.length
  try {
    const result = await uploadDataFiles(
      files,
      filesStore.currentPath || undefined,
      (done, total) => {
        uploadDone.value = done
        uploadTotal.value = total
      },
    )
    await filesStore.loadFiles()
    if (result.ok) {
      message.success(result.ok === 1 ? '已上传 1 项' : `已上传 ${result.ok} 项`)
    }
    if (result.fail) {
      message.warning(`${result.fail} 项失败`)
    }
    for (const err of (result.errors || []).slice(0, 5)) {
      message.error(err)
    }
    if ((result.errors || []).length > 5) {
      message.error(`另有 ${result.errors.length - 5} 条错误未全部展示`)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploading.value = false
    uploadDone.value = 0
    uploadTotal.value = 0
  }
}
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';
@import '@/styles/mixins.scss';

.right-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  max-height: 100%;
  background-repeat: no-repeat;
  background-position: right bottom;
  background-size: cover;
  -webkit-backdrop-filter: blur(20px);
  border-left: 1px solid var(--border-subtle);
  overflow: hidden;
  overscroll-behavior: none;
  transform: translateZ(0);
  box-shadow: 0px 4px 10px 0px rgba(0, 0, 0, 0.15);
  z-index: 9;
  position: relative;
  transition: width 0.22s ease;
  flex: 0 0 auto;
  flex-shrink: 0;
  box-sizing: border-box;

  &.collapsed {
    box-shadow: none;
  }

  .collapsed-rail {
    width: 100%;
    height: 100%;
    border: 0;
    padding: 16px 0;
    background: transparent;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    color: var(--panel-text, #334155);

    &:hover {
      background: rgba(59, 130, 246, 0.06);
    }

    .rail-icon {
      width: 18px;
      height: 18px;
      opacity: 0.75;
    }

    .rail-text {
      writing-mode: vertical-rl;
      text-orientation: mixed;
      font-size: var(--font-size-sm);
      font-weight: var(--font-weight-semibold);
      letter-spacing: 0.12em;
      opacity: 0.85;
    }
  }

  .panel-content {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
    min-width: 280px;
  }

  .panel-section {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 0 16px 12px;
    overflow: hidden;

    &:first-child {
      border-bottom: 1px solid var(--border-subtle);
    }

    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: 56px;

      .section-title {
        font-size: var(--font-size-lg);
        font-weight: var(--font-weight-semibold);
        color: var(--panel-text);
        display: flex;
        align-items: center;
        gap: 6px;
      }

      .section-icon {
        width: var(--icon-size-md);
        height: var(--icon-size-md);
        display: block;
        object-fit: contain;
        flex-shrink: 0;
      }

      .section-actions {
        display: flex;
        gap: 4px;

        .hidden-file {
          display: none;
        }

        .action-btn {
          @include icon-btn(var(--icon-btn-size));
          color: var(--panel-text);
          opacity: 0.6;
          border: none;
          background: transparent;
          border-radius: 6px;
          cursor: pointer;

          &:hover {
            opacity: 1;
            background: rgba(59, 130, 246, 0.08);
          }
        }

        .collapse-icon {
          width: var(--icon-size-lg);
          height: var(--icon-size-lg);
          display: block;
          opacity: 0.85;
        }
      }
    }
  }
}

.upload-progress {
  margin: 0 0 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;

  .upload-hint {
    font-size: var(--font-size-xs);
    color: var(--text-muted, #64748b);
  }
}

.folder-hint {
  margin: 0 0 10px;
  font-size: var(--font-size-xs);
  color: var(--text-muted, #64748b);
}
</style>
