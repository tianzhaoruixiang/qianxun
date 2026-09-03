<template>
  <div class="file-search">
    <a-input
      v-model:value="store.keyword"
      allow-clear
      placeholder="搜索文件或文件夹"
      class="search-input"
    >
      <template #prefix>
        <AppGlyph name="search" size="sm" class="icon" />
      </template>
    </a-input>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import AppGlyph from '@/components/AppGlyph.vue'
import { useDataFilesStore } from '@/stores/useDataFilesStore'

const store = useDataFilesStore()

onMounted(() => {
  void store.loadFiles()
})
</script>

<style scoped lang="scss">
.file-search {
  padding: 0 0 8px;

  .search-input {
    border-radius: 8px;
  }

  .icon {
    color: var(--text-muted, #94a3b8);
  }
}
</style>
