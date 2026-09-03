<template>
  <span class="ft-icon" :class="`ft-${type}`" :aria-hidden="true">
    <!-- 文件夹：Windows 资源管理器风格 -->
    <svg v-if="type === 'folder'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('fd-back')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#F6D264" />
          <stop offset="100%" stop-color="#E8B33A" />
        </linearGradient>
        <linearGradient :id="gid('fd-front')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFE9A0" />
          <stop offset="45%" stop-color="#F7D060" />
          <stop offset="100%" stop-color="#EBB040" />
        </linearGradient>
        <linearGradient :id="gid('fd-tab')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFE07A" />
          <stop offset="100%" stop-color="#F0C24A" />
        </linearGradient>
      </defs>
      <!-- 后盖 -->
      <path
        d="M8 18c0-2.2 1.8-4 4-4h16.5c1.3 0 2.5.6 3.2 1.7l1.6 2.3c.4.6 1.1.9 1.8.9H52c2.2 0 4 1.8 4 4v26.5c0 2.5-2 4.5-4.5 4.5h-39C8 54 6 52 6 49.5V18z"
        :fill="url('fd-back')"
      />
      <!-- 左上标签页 -->
      <path
        d="M10 14h16.2c1.1 0 2.1.5 2.7 1.4l1.3 1.9c.3.4.8.7 1.3.7H34v4.5H8.5c-1.4 0-2.5-1.1-2.5-2.5V18c0-2.2 1.8-4 4-4z"
        :fill="url('fd-tab')"
      />
      <!-- 前盖（略下移，形成厚度） -->
      <path
        d="M6 26.5c0-1.9 1.6-3.5 3.5-3.5h45c1.9 0 3.5 1.6 3.5 3.5V49c0 2.8-2.2 5-5 5H11c-2.8 0-5-2.2-5-5V26.5z"
        :fill="url('fd-front')"
      />
      <!-- 顶沿高光 -->
      <path d="M9.5 23h45c1.4 0 2.5 1.1 2.5 2.5v1.2H7V25.5c0-1.4 1.1-2.5 2.5-2.5z" fill="#FFF6C8" opacity=".7" />
      <!-- 底部阴影 -->
      <path d="M8 48.5h48c0 2.5-2 4.5-4.5 4.5h-39C8 53 6 51 6 48.5h2z" fill="#D49A28" opacity=".28" />
    </svg>

    <!-- Word：白纸 + 蓝角标 -->
    <svg v-else-if="type === 'word'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('w-paper')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFFFFF" />
          <stop offset="100%" stop-color="#EEF3FA" />
        </linearGradient>
      </defs>
      <path d="M14 6h26l12 12v38c0 2.2-1.8 4-4 4H14c-2.2 0-4-1.8-4-4V10c0-2.2 1.8-4 4-4z" :fill="url('w-paper')" stroke="#D6E0EF" stroke-width="1.2" />
      <path d="M40 6v10c0 1.1.9 2 2 2h10L40 6z" fill="#C5D4EA" />
      <rect x="10" y="34" width="36" height="22" rx="4" fill="#2B579A" />
      <text x="28" y="50" text-anchor="middle" fill="#fff" font-size="14" font-weight="700" font-family="Arial,Helvetica,sans-serif">W</text>
      <path d="M18 16h18v2.2H18V16zm0 6h22v2.2H18V22zm0 6h14v2.2H18V28z" fill="#A8B8D0" />
    </svg>

    <!-- Excel -->
    <svg v-else-if="type === 'excel'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('x-paper')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFFFFF" />
          <stop offset="100%" stop-color="#EAF6EF" />
        </linearGradient>
      </defs>
      <path d="M14 6h26l12 12v38c0 2.2-1.8 4-4 4H14c-2.2 0-4-1.8-4-4V10c0-2.2 1.8-4 4-4z" :fill="url('x-paper')" stroke="#CDE8D8" stroke-width="1.2" />
      <path d="M40 6v10c0 1.1.9 2 2 2h10L40 6z" fill="#B7DFC6" />
      <rect x="10" y="34" width="36" height="22" rx="4" fill="#1D6F42" />
      <text x="28" y="50" text-anchor="middle" fill="#fff" font-size="14" font-weight="700" font-family="Arial,Helvetica,sans-serif">X</text>
      <path d="M18 16h18" stroke="#A8CDB8" stroke-width="2.2" stroke-linecap="round" />
      <path d="M18 22h22" stroke="#A8CDB8" stroke-width="2.2" stroke-linecap="round" />
      <path d="M18 28h14" stroke="#A8CDB8" stroke-width="2.2" stroke-linecap="round" />
    </svg>

    <!-- PDF -->
    <svg v-else-if="type === 'pdf'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('p-paper')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFFFFF" />
          <stop offset="100%" stop-color="#FBEDED" />
        </linearGradient>
      </defs>
      <path d="M14 6h26l12 12v38c0 2.2-1.8 4-4 4H14c-2.2 0-4-1.8-4-4V10c0-2.2 1.8-4 4-4z" :fill="url('p-paper')" stroke="#F0C8C8" stroke-width="1.2" />
      <path d="M40 6v10c0 1.1.9 2 2 2h10L40 6z" fill="#F0B4B4" />
      <rect x="8" y="34" width="40" height="22" rx="4" fill="#E5252A" />
      <text x="28" y="49.5" text-anchor="middle" fill="#fff" font-size="11" font-weight="700" font-family="Arial,Helvetica,sans-serif">PDF</text>
      <path d="M18 16h18M18 22h22M18 28h14" stroke="#E0A8A8" stroke-width="2.2" stroke-linecap="round" />
    </svg>

    <!-- PPT -->
    <svg v-else-if="type === 'ppt'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('ppt-paper')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFFFFF" />
          <stop offset="100%" stop-color="#FBEDE6" />
        </linearGradient>
      </defs>
      <path d="M14 6h26l12 12v38c0 2.2-1.8 4-4 4H14c-2.2 0-4-1.8-4-4V10c0-2.2 1.8-4 4-4z" :fill="url('ppt-paper')" stroke="#F0D0C0" stroke-width="1.2" />
      <path d="M40 6v10c0 1.1.9 2 2 2h10L40 6z" fill="#E8B8A0" />
      <rect x="10" y="34" width="36" height="22" rx="4" fill="#C43E1C" />
      <text x="28" y="50" text-anchor="middle" fill="#fff" font-size="13" font-weight="700" font-family="Arial,Helvetica,sans-serif">P</text>
      <rect x="18" y="16" width="20" height="12" rx="1.5" fill="none" stroke="#E0B098" stroke-width="2" />
    </svg>

    <!-- 文本 -->
    <svg v-else-if="type === 'text'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('t-paper')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFFFFF" />
          <stop offset="100%" stop-color="#F1F3F6" />
        </linearGradient>
      </defs>
      <path d="M14 6h26l12 12v38c0 2.2-1.8 4-4 4H14c-2.2 0-4-1.8-4-4V10c0-2.2 1.8-4 4-4z" :fill="url('t-paper')" stroke="#D5DAE1" stroke-width="1.2" />
      <path d="M40 6v10c0 1.1.9 2 2 2h10L40 6z" fill="#C8CED6" />
      <rect x="10" y="34" width="36" height="22" rx="4" fill="#5B6B7C" />
      <text x="28" y="50" text-anchor="middle" fill="#fff" font-size="12" font-weight="700" font-family="Arial,Helvetica,sans-serif">TXT</text>
      <path d="M18 16h18M18 22h22M18 28h14" stroke="#B8C0CA" stroke-width="2.2" stroke-linecap="round" />
    </svg>

    <!-- 图片 -->
    <svg v-else-if="type === 'image'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('img-bg')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#8B5CF6" />
          <stop offset="100%" stop-color="#6D28D9" />
        </linearGradient>
      </defs>
      <rect x="8" y="10" width="48" height="44" rx="8" :fill="url('img-bg')" />
      <circle cx="22" cy="24" r="5" fill="#FDE68A" />
      <path d="M12 46l12-14 8 9 6-7 14 12H12z" fill="#E9D5FF" />
      <rect x="8" y="42" width="48" height="12" fill="#5B21B6" opacity=".25" />
    </svg>

    <!-- 邮件 -->
    <svg v-else-if="type === 'eml'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('mail-bg')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FBBF24" />
          <stop offset="100%" stop-color="#F59E0B" />
        </linearGradient>
      </defs>
      <rect x="6" y="16" width="52" height="36" rx="6" :fill="url('mail-bg')" />
      <path d="M10 20l22 16 22-16" fill="none" stroke="#fff" stroke-width="3" stroke-linejoin="round" stroke-linecap="round" />
      <path d="M10 48l16-14M54 48L38 34" fill="none" stroke="#fff" stroke-width="2.4" opacity=".85" stroke-linecap="round" />
    </svg>

    <!-- 压缩包 -->
    <svg v-else-if="type === 'archive'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('zip-paper')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFFFFF" />
          <stop offset="100%" stop-color="#F8EFE4" />
        </linearGradient>
      </defs>
      <path d="M14 6h26l12 12v38c0 2.2-1.8 4-4 4H14c-2.2 0-4-1.8-4-4V10c0-2.2 1.8-4 4-4z" :fill="url('zip-paper')" stroke="#E8D2B8" stroke-width="1.2" />
      <path d="M40 6v10c0 1.1.9 2 2 2h10L40 6z" fill="#E0C4A0" />
      <rect x="26" y="14" width="10" height="6" fill="#F5D78A" />
      <rect x="26" y="20" width="10" height="6" fill="#8B5A2B" />
      <rect x="26" y="26" width="10" height="6" fill="#F5D78A" />
      <rect x="10" y="34" width="36" height="22" rx="4" fill="#B45309" />
      <text x="28" y="50" text-anchor="middle" fill="#fff" font-size="12" font-weight="700" font-family="Arial,Helvetica,sans-serif">ZIP</text>
    </svg>

    <!-- 音频 -->
    <svg v-else-if="type === 'voice'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('aud-bg')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#2DD4BF" />
          <stop offset="100%" stop-color="#0D9488" />
        </linearGradient>
      </defs>
      <rect x="8" y="10" width="48" height="44" rx="8" :fill="url('aud-bg')" />
      <rect x="28" y="20" width="8" height="18" rx="4" fill="#fff" />
      <path d="M22 32a10 10 0 0 0 20 0" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" />
      <path d="M32 42v5" stroke="#fff" stroke-width="3" stroke-linecap="round" />
      <path d="M26 47h12" stroke="#fff" stroke-width="3" stroke-linecap="round" />
    </svg>

    <!-- 默认文件 -->
    <svg v-else viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('f-paper')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFFFFF" />
          <stop offset="100%" stop-color="#F1F5F9" />
        </linearGradient>
      </defs>
      <path d="M14 6h26l12 12v38c0 2.2-1.8 4-4 4H14c-2.2 0-4-1.8-4-4V10c0-2.2 1.8-4 4-4z" :fill="url('f-paper')" stroke="#D0D7E2" stroke-width="1.2" />
      <path d="M40 6v10c0 1.1.9 2 2 2h10L40 6z" fill="#C5CDD8" />
      <rect x="10" y="34" width="36" height="22" rx="4" fill="#64748B" />
      <text x="28" y="50" text-anchor="middle" fill="#fff" font-size="11" font-weight="700" font-family="Arial,Helvetica,sans-serif">FILE</text>
      <path d="M18 16h18M18 22h22M18 28h14" stroke="#B8C0CC" stroke-width="2.2" stroke-linecap="round" />
    </svg>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ kind?: string }>()

const uid = `i${Math.random().toString(36).slice(2, 9)}`

function gid(name: string) {
  return `${uid}-${name}`
}

function url(name: string) {
  return `url(#${gid(name)})`
}

const type = computed(() => {
  const k = (props.kind ?? '').trim().toLowerCase()
  if (k === 'folder') return 'folder'
  if (k === 'word' || k === 'doc' || k === 'docx') return 'word'
  if (k === 'excel' || k === 'xls' || k === 'xlsx' || k === 'csv') return 'excel'
  if (k === 'pdf') return 'pdf'
  if (k === 'ppt' || k === 'pptx') return 'ppt'
  if (k === 'text' || k === 'txt' || k === 'md' || k === 'json' || k === 'xml' || k === 'html' || k === 'htm') return 'text'
  if (k === 'image' || k === 'png' || k === 'jpg' || k === 'jpeg' || k === 'gif' || k === 'webp' || k === 'bmp' || k === 'svg') return 'image'
  if (k === 'eml' || k === 'msg') return 'eml'
  if (k === 'archive' || k === 'zip' || k === 'rar' || k === '7z' || k === 'tar' || k === 'gz') return 'archive'
  if (k === 'voice' || k === 'audio' || k === 'mp3' || k === 'wav') return 'voice'
  return 'file'
})
</script>

<style scoped lang="scss">
.ft-icon {
  display: inline-flex;
  width: 34px;
  height: 34px;
  flex: none;
  line-height: 0;
  filter: drop-shadow(0 1px 2px rgba(15, 23, 42, 0.14));

  svg {
    width: 100%;
    height: 100%;
    display: block;
  }
}
</style>
