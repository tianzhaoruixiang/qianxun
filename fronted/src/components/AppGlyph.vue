<template>
  <span class="ag-icon" :class="[`ag-${name}`, `ag-${sizeClass}`]" :style="sizeStyle" aria-hidden="true">
    <FileTypeIcon v-if="fileKind" :kind="fileKind" />

    <!-- 智能体 -->
    <svg v-else-if="name === 'agent'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('ag')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#60A5FA" />
          <stop offset="100%" stop-color="#2563EB" />
        </linearGradient>
      </defs>
      <rect x="8" y="14" width="48" height="40" rx="12" :fill="url('ag')" />
      <rect x="16" y="22" width="32" height="20" rx="8" fill="#E8F1FF" />
      <circle cx="26" cy="32" r="4" fill="#1D4ED8" />
      <circle cx="38" cy="32" r="4" fill="#1D4ED8" />
      <rect x="24" y="46" width="16" height="4" rx="2" fill="#BFDBFE" />
      <rect x="30" y="8" width="4" height="8" rx="2" fill="#93C5FD" />
      <circle cx="32" cy="8" r="3.5" fill="#FBBF24" />
    </svg>

    <!-- 技能：能力徽章 + 双星闪耀 -->
    <svg v-else-if="name === 'skill'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('sk-bg')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#A78BFA" />
          <stop offset="55%" stop-color="#7C3AED" />
          <stop offset="100%" stop-color="#4F46E5" />
        </linearGradient>
        <linearGradient :id="gid('sk-star')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFFBEB" />
          <stop offset="100%" stop-color="#FDE68A" />
        </linearGradient>
        <linearGradient :id="gid('sk-glow')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#F0ABFC" />
          <stop offset="100%" stop-color="#C4B5FD" />
        </linearGradient>
      </defs>
      <rect x="8" y="8" width="48" height="48" rx="16" :fill="url('sk-bg')" />
      <circle cx="46" cy="18" r="10" :fill="url('sk-glow')" opacity=".35" />
      <path
        d="M32 14.5l3.2 9.2h9.7l-7.85 5.7 3 9.1L32 33.7l-7.05 4.8 3-9.1-7.85-5.7h9.7L32 14.5z"
        :fill="url('sk-star')"
      />
      <path
        d="M48 28l1.4 4h4.2l-3.4 2.45 1.3 3.95L48 35.9l-3.5 2.5 1.3-3.95-3.4-2.45h4.2L48 28z"
        fill="#F5D0FE"
        opacity=".95"
      />
      <path
        d="M18 40l1.1 3.1h3.3l-2.65 1.9 1 3.05L18 46.1l-2.75 1.95 1-3.05-2.65-1.9h3.3L18 40z"
        fill="#DDD6FE"
        opacity=".9"
      />
      <rect x="22" y="48" width="20" height="4" rx="2" fill="#EDE9FE" opacity=".55" />
    </svg>

    <!-- 工具：扳手 + 螺丝刀交叉 -->
    <svg v-else-if="name === 'tool'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('tl-bg')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#FFF7ED" />
          <stop offset="100%" stop-color="#FFEDD5" />
        </linearGradient>
        <linearGradient :id="gid('tl-metal')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#CBD5E1" />
          <stop offset="45%" stop-color="#64748B" />
          <stop offset="100%" stop-color="#334155" />
        </linearGradient>
        <linearGradient :id="gid('tl-handle')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FDBA74" />
          <stop offset="100%" stop-color="#EA580C" />
        </linearGradient>
      </defs>
      <rect x="8" y="8" width="48" height="48" rx="16" :fill="url('tl-bg')" />
      <circle cx="18" cy="46" r="12" fill="#FED7AA" opacity=".45" />
      <!-- wrench -->
      <g transform="rotate(-38 30 30)">
        <path
          d="M36 14c-4.2 0-7.8 2.6-9.2 6.3l-.5 1.3 7.1 7.1 1.3-.5C38.4 26.8 41 23.2 41 19c0-1-.2-2-.5-2.9l-4.4 4.4-3.6-3.6 4.4-4.4c-.9-.3-1.9-.5-2.9-.5z"
          :fill="url('tl-metal')"
        />
        <rect x="22" y="26" width="8" height="22" rx="3.5" :fill="url('tl-metal')" />
        <rect x="23.5" y="28" width="5" height="18" rx="2.5" fill="#94A3B8" opacity=".35" />
      </g>
      <!-- screwdriver -->
      <g transform="rotate(42 34 34)">
        <rect x="31" y="16" width="6" height="18" rx="2" :fill="url('tl-handle')" />
        <rect x="31.5" y="17.5" width="2" height="15" rx="1" fill="#FFEDD5" opacity=".5" />
        <path d="M30 34h8l-1.5 4h-5L30 34z" fill="#475569" />
        <rect x="32.5" y="38" width="3" height="12" rx="1.2" :fill="url('tl-metal')" />
      </g>
      <circle cx="48" cy="18" r="3" fill="#FB923C" opacity=".85" />
    </svg>

    <!-- 插件：拼图块 -->
    <svg v-else-if="name === 'plugin'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('pg-bg')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#5EEAD4" />
          <stop offset="55%" stop-color="#14B8A6" />
          <stop offset="100%" stop-color="#0F766E" />
        </linearGradient>
      </defs>
      <rect x="8" y="8" width="48" height="48" rx="16" :fill="url('pg-bg')" />
      <path
        d="M22 24h10.5c.4-2.4 2.4-4.2 4.9-4.2 2.7 0 4.9 2.2 4.9 4.9 0 1.1-.4 2.1-1 2.9H48v10.5c-2.4.4-4.2 2.4-4.2 4.9 0 2.7 2.2 4.9 4.9 4.9 1.1 0 2.1-.4 2.9-1V52H22V41.5c2.4-.4 4.2-2.4 4.2-4.9 0-2.7-2.2-4.9-4.9-4.9-1.1 0-2.1.4-2.9 1V24z"
        fill="#ECFEFF"
        opacity=".95"
      />
    </svg>

    <!-- 搜索 -->
    <svg v-else-if="name === 'search'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <circle cx="28" cy="28" r="14" fill="none" stroke="#3B82F6" stroke-width="6" />
      <circle cx="28" cy="28" r="8" fill="#DBEAFE" />
      <path d="M38 38l14 14" stroke="#1D4ED8" stroke-width="7" stroke-linecap="round" />
    </svg>

    <!-- 刷新 -->
    <svg v-else-if="name === 'reload'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <circle cx="32" cy="32" r="22" fill="#EFF6FF" />
      <path
        d="M46 28a14 14 0 1 0 2 10"
        fill="none"
        stroke="#2563EB"
        stroke-width="5"
        stroke-linecap="round"
      />
      <path d="M46 18v12h-12" fill="none" stroke="#38BDF8" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
    </svg>

    <!-- 新增 -->
    <svg v-else-if="name === 'plus'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('pl')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#60A5FA" />
          <stop offset="100%" stop-color="#2563EB" />
        </linearGradient>
      </defs>
      <rect x="8" y="8" width="48" height="48" rx="14" :fill="url('pl')" />
      <path d="M32 18v28M18 32h28" stroke="#fff" stroke-width="6" stroke-linecap="round" />
    </svg>

    <!-- 上传 -->
    <svg v-else-if="name === 'upload'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="10" y="36" width="44" height="16" rx="6" fill="#BFDBFE" />
      <path d="M32 12l12 14H38v12H26V26H20L32 12z" fill="#2563EB" />
      <rect x="16" y="48" width="32" height="5" rx="2.5" fill="#3B82F6" />
    </svg>

    <!-- 下载 -->
    <svg v-else-if="name === 'download'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="10" y="42" width="44" height="12" rx="5" fill="#93C5FD" />
      <path d="M32 10v26" stroke="#2563EB" stroke-width="6" stroke-linecap="round" />
      <path d="M20 28l12 14 12-14" fill="none" stroke="#1D4ED8" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" />
    </svg>

    <!-- 预览 -->
    <svg v-else-if="name === 'preview'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <ellipse cx="32" cy="32" rx="24" ry="14" fill="#DBEAFE" />
      <ellipse cx="32" cy="32" rx="24" ry="14" fill="none" stroke="#3B82F6" stroke-width="4" />
      <circle cx="32" cy="32" r="8" fill="#1D4ED8" />
      <circle cx="35" cy="29" r="2.5" fill="#BFDBFE" />
    </svg>

    <!-- 删除 -->
    <svg v-else-if="name === 'delete'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="16" y="20" width="32" height="34" rx="6" fill="#FCA5A5" />
      <rect x="20" y="26" width="6" height="22" rx="2" fill="#fff" opacity=".85" />
      <rect x="29" y="26" width="6" height="22" rx="2" fill="#fff" opacity=".85" />
      <rect x="38" y="26" width="6" height="22" rx="2" fill="#fff" opacity=".85" />
      <rect x="12" y="14" width="40" height="8" rx="3" fill="#EF4444" />
      <rect x="26" y="8" width="12" height="8" rx="3" fill="#F87171" />
    </svg>

    <!-- 更多 -->
    <svg v-else-if="name === 'more'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <circle cx="16" cy="32" r="6" fill="#94A3B8" />
      <circle cx="32" cy="32" r="6" fill="#3B82F6" />
      <circle cx="48" cy="32" r="6" fill="#94A3B8" />
    </svg>

    <!-- 新建文件夹（Windows 风格 + 加号） -->
    <svg v-else-if="name === 'folderAdd'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('fa-back')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#F6D264" />
          <stop offset="100%" stop-color="#E8B33A" />
        </linearGradient>
        <linearGradient :id="gid('fa-front')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#FFE9A0" />
          <stop offset="45%" stop-color="#F7D060" />
          <stop offset="100%" stop-color="#EBB040" />
        </linearGradient>
      </defs>
      <path
        d="M8 18c0-2.2 1.8-4 4-4h16.5c1.3 0 2.5.6 3.2 1.7l1.6 2.3c.4.6 1.1.9 1.8.9H52c2.2 0 4 1.8 4 4v26.5c0 2.5-2 4.5-4.5 4.5h-39C8 54 6 52 6 49.5V18z"
        :fill="url('fa-back')"
      />
      <path
        d="M10 14h16.2c1.1 0 2.1.5 2.7 1.4l1.3 1.9c.3.4.8.7 1.3.7H34v4.5H8.5c-1.4 0-2.5-1.1-2.5-2.5V18c0-2.2 1.8-4 4-4z"
        fill="#FFE07A"
      />
      <path
        d="M6 26.5c0-1.9 1.6-3.5 3.5-3.5h45c1.9 0 3.5 1.6 3.5 3.5V49c0 2.8-2.2 5-5 5H11c-2.8 0-5-2.2-5-5V26.5z"
        :fill="url('fa-front')"
      />
      <path d="M9.5 23h45c1.4 0 2.5 1.1 2.5 2.5v1.2H7V25.5c0-1.4 1.1-2.5 2.5-2.5z" fill="#FFF6C8" opacity=".7" />
      <circle cx="46" cy="42" r="12" fill="#2563EB" />
      <path d="M46 36v12M40 42h12" stroke="#fff" stroke-width="3.5" stroke-linecap="round" />
    </svg>

    <!-- 空态 -->
    <svg v-else-if="name === 'empty'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <path d="M10 24h44l-4 26H14L10 24z" fill="#E2E8F0" />
      <path d="M18 24l6-10h16l6 10" fill="#CBD5E1" />
      <path d="M10 24h44v6H10z" fill="#93C5FD" />
      <rect x="24" y="36" width="16" height="8" rx="2" fill="#3B82F6" opacity=".55" />
    </svg>

    <!-- 智能体节点 -->
    <svg v-else-if="name === 'cluster'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <path d="M20 22h24v8H20z" fill="#93C5FD" />
      <circle cx="18" cy="42" r="8" fill="#2563EB" />
      <circle cx="46" cy="42" r="8" fill="#38BDF8" />
      <path d="M26 42h12" stroke="#64748B" stroke-width="3" />
      <path d="M32 30v8" stroke="#64748B" stroke-width="3" />
    </svg>

    <!-- 排序 -->
    <svg v-else-if="name === 'sort'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="12" y="16" width="28" height="6" rx="3" fill="#93C5FD" />
      <rect x="12" y="29" width="22" height="6" rx="3" fill="#60A5FA" />
      <rect x="12" y="42" width="16" height="6" rx="3" fill="#2563EB" />
      <path d="M48 16v24" stroke="#F59E0B" stroke-width="5" stroke-linecap="round" />
      <path d="M40 32l8 12 8-12" fill="none" stroke="#F59E0B" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
    </svg>

    <!-- 分类格子 -->
    <svg v-else-if="name === 'grid'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="10" y="10" width="18" height="18" rx="5" fill="#60A5FA" />
      <rect x="36" y="10" width="18" height="18" rx="5" fill="#38BDF8" />
      <rect x="10" y="36" width="18" height="18" rx="5" fill="#FBBF24" />
      <rect x="36" y="36" width="18" height="18" rx="5" fill="#34D399" />
    </svg>

    <!-- 附件 -->
    <svg v-else-if="name === 'clip'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M22 28v16c0 5.5 4.5 10 10 10s10-4.5 10-10V20c0-3.3-2.7-6-6-6s-6 2.7-6 6v22c0 1.1.9 2 2 2s2-.9 2-2V24"
        fill="none"
        stroke="#2563EB"
        stroke-width="5"
        stroke-linecap="round"
      />
    </svg>

    <!-- 停止 -->
    <svg v-else-if="name === 'stop'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <circle cx="32" cy="32" r="22" fill="#EF4444" />
      <rect x="22" y="22" width="20" height="20" rx="4" fill="#fff" />
    </svg>

    <!-- 对话 -->
    <svg v-else-if="name === 'chat'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('ch')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#60A5FA" />
          <stop offset="100%" stop-color="#2563EB" />
        </linearGradient>
      </defs>
      <path d="M12 14h40a8 8 0 0 1 8 8v18a8 8 0 0 1-8 8H28l-12 10v-10H12a8 8 0 0 1-8-8V22a8 8 0 0 1 8-8z" :fill="url('ch')" />
      <circle cx="24" cy="32" r="3.5" fill="#fff" />
      <circle cx="32" cy="32" r="3.5" fill="#fff" />
      <circle cx="40" cy="32" r="3.5" fill="#fff" />
    </svg>

    <!-- 超市 -->
    <svg v-else-if="name === 'market'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="10" y="24" width="44" height="28" rx="6" fill="#EEF2FF" />
      <path d="M12 24l6-12h28l6 12" fill="#818CF8" />
      <rect x="18" y="30" width="10" height="16" rx="2" fill="#4F46E5" />
      <rect x="36" y="30" width="10" height="16" rx="2" fill="#60A5FA" />
    </svg>

    <!-- 分析 -->
    <svg v-else-if="name === 'analysis'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="8" y="8" width="48" height="48" rx="14" fill="#FEF3C7" />
      <rect x="16" y="36" width="8" height="14" rx="2" fill="#F59E0B" />
      <rect x="28" y="26" width="8" height="24" rx="2" fill="#D97706" />
      <rect x="40" y="18" width="8" height="32" rx="2" fill="#FBBF24" />
    </svg>

    <!-- 浏览器 -->
    <svg v-else-if="name === 'browser'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <circle cx="32" cy="32" r="22" fill="#DBEAFE" />
      <circle cx="32" cy="32" r="22" fill="none" stroke="#2563EB" stroke-width="4" />
      <ellipse cx="32" cy="32" rx="10" ry="22" fill="none" stroke="#38BDF8" stroke-width="3" />
      <path d="M12 32h40M16 22h32M16 42h32" stroke="#3B82F6" stroke-width="2.5" />
    </svg>

    <!-- 终端 -->
    <svg v-else-if="name === 'terminal'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="8" y="12" width="48" height="40" rx="8" fill="#0F172A" />
      <rect x="8" y="12" width="48" height="10" rx="8" fill="#1E293B" />
      <circle cx="16" cy="17" r="2" fill="#F87171" />
      <circle cx="22" cy="17" r="2" fill="#FBBF24" />
      <circle cx="28" cy="17" r="2" fill="#34D399" />
      <path d="M18 32l8 6-8 6" fill="none" stroke="#38BDF8" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M30 44h14" stroke="#64748B" stroke-width="3.5" stroke-linecap="round" />
    </svg>

    <!-- 网页搜索 -->
    <svg v-else-if="name === 'web'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <circle cx="28" cy="28" r="16" fill="#CFFAFE" />
      <circle cx="28" cy="28" r="16" fill="none" stroke="#0D9488" stroke-width="4" />
      <path d="M16 28h24M28 14c-5 6-5 16 0 28M28 14c5 6 5 16 0 28" fill="none" stroke="#14B8A6" stroke-width="2.5" />
      <path d="M38 38l12 12" stroke="#2563EB" stroke-width="6" stroke-linecap="round" />
    </svg>

    <!-- 桌面 -->
    <svg v-else-if="name === 'desktop'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="8" y="10" width="48" height="32" rx="6" fill="#1E3A8A" />
      <rect x="12" y="14" width="40" height="24" rx="3" fill="#93C5FD" />
      <rect x="26" y="42" width="12" height="6" fill="#64748B" />
      <rect x="18" y="48" width="28" height="5" rx="2.5" fill="#475569" />
    </svg>

    <svg v-else-if="name === 'goal'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <circle cx="32" cy="32" r="22" fill="#DBEAFE" />
      <circle cx="32" cy="32" r="22" fill="none" stroke="#2563EB" stroke-width="3" />
      <circle cx="32" cy="32" r="12" fill="none" stroke="#3B82F6" stroke-width="3" />
      <circle cx="32" cy="32" r="4" fill="#1D4ED8" />
    </svg>

    <!-- 成功 -->
    <svg v-else-if="name === 'success'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('ok')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#4ADE80" />
          <stop offset="100%" stop-color="#16A34A" />
        </linearGradient>
      </defs>
      <circle cx="32" cy="32" r="24" :fill="url('ok')" />
      <path
        d="M20 33l8 8 16-18"
        fill="none"
        stroke="#fff"
        stroke-width="6"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>

    <!-- 失败 -->
    <svg v-else-if="name === 'fail'" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient :id="gid('fl')" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#F87171" />
          <stop offset="100%" stop-color="#DC2626" />
        </linearGradient>
      </defs>
      <circle cx="32" cy="32" r="24" :fill="url('fl')" />
      <path
        d="M24 24l16 16M40 24L24 40"
        fill="none"
        stroke="#fff"
        stroke-width="6"
        stroke-linecap="round"
      />
    </svg>

    <svg v-else viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="10" y="8" width="44" height="48" rx="8" fill="#F1F5F9" stroke="#CBD5E1" stroke-width="2" />
      <path d="M20 22h24M20 32h24M20 42h16" stroke="#94A3B8" stroke-width="4" stroke-linecap="round" />
    </svg>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import FileTypeIcon from '@/components/FileTypeIcon.vue'

const props = withDefaults(
  defineProps<{
    name: string
    size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | number
  }>(),
  { size: 'md' },
)

const uid = `g${Math.random().toString(36).slice(2, 9)}`

function gid(name: string) {
  return `${uid}-${name}`
}

function url(name: string) {
  return `url(#${gid(name)})`
}

const fileKind = computed(() => {
  switch (props.name) {
    case 'folder':
      return 'folder'
    case 'file':
      return 'file'
    case 'zip':
      return 'archive'
    case 'image':
      return 'image'
    case 'document':
      return 'word'
    default:
      return ''
  }
})

const sizeClass = computed(() => (typeof props.size === 'number' ? 'custom' : props.size))

const sizeStyle = computed(() => {
  if (typeof props.size !== 'number') return undefined
  return { width: `${props.size}px`, height: `${props.size}px` }
})
</script>

<style scoped lang="scss">
.ag-icon {
  display: inline-flex;
  flex: none;
  flex-shrink: 0;
  line-height: 0;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
  box-sizing: border-box;
  filter: drop-shadow(0 0.5px 1px rgba(0, 0, 0, 0.08));
  transition: transform 0.22s cubic-bezier(0.22, 1, 0.36, 1), filter 0.22s ease;

  svg,
  :deep(.ft-icon) {
    width: 100%;
    height: 100%;
    display: block;
    filter: none;
  }

  &.ag-xs {
    width: var(--icon-size-xs, 14px);
    height: var(--icon-size-xs, 14px);
  }

  &.ag-sm {
    width: var(--icon-size-sm, 16px);
    height: var(--icon-size-sm, 16px);
  }

  &.ag-md {
    width: var(--icon-size-md, 18px);
    height: var(--icon-size-md, 18px);
  }

  &.ag-lg {
    width: var(--icon-size-lg, 24px);
    height: var(--icon-size-lg, 24px);
  }

  &.ag-xl {
    width: var(--icon-size-xl, 36px);
    height: var(--icon-size-xl, 36px);
  }
}

button:hover .ag-icon,
a:hover .ag-icon,
.tab:hover .ag-icon,
.act:hover .ag-icon,
.preview-btn:hover .ag-icon,
.action-btn:hover .ag-icon,
.refresh-btn:hover .ag-icon,
.quick-card:hover .ag-icon,
.icon-pick:hover .ag-icon,
.cat-icon:hover .ag-icon,
.skill-card:hover .ag-icon,
.tool-card:hover .ag-icon,
.plugin-card:hover .ag-icon,
.mcp-card:hover .ag-icon,
.agent-card:hover .ag-icon {
  transform: translateY(-0.5px) scale(1.04);
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.12));
}
</style>
