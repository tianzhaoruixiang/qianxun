<template>
  <div class="file-stats">
    <span>共 {{ store.fileCount }} 个文件</span>
    <span v-if="store.folderCount">，{{ store.folderCount }} 个文件夹</span>
    <span v-if="store.keyword.trim()">，筛选 {{ store.filteredFiles.length }} 项</span>
    <span v-else-if="store.currentPath">，当前目录 {{ store.filteredFiles.length }} 项</span>
  </div>
</template>

<script setup lang="ts">
import { useDataFilesStore } from '@/stores/useDataFilesStore'

const store = useDataFilesStore()
</script>

<style scoped lang="scss">
.file-stats {
  font-size: var(--font-size-xs);
  color: var(--text-muted, #94a3b8);
  padding-top: 6px;
  flex-shrink: 0;
}
</style>
