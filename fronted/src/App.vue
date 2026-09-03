<template>
  <ConfigProvider :theme="antdTheme">
    <router-view />
  </ConfigProvider>
</template>

<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'

const antdTheme = {
  token: {
    fontFamily: 'var(--font-family-base)',
    fontSize: 14,
    colorText: 'var(--text-primary)',
    colorTextSecondary: 'var(--text-secondary)',
    colorTextTertiary: 'var(--text-muted)',
    fontWeightStrong: 600,
    lineHeight: 1.5,
  },
}
</script>
