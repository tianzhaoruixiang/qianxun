<template>
  <div class="login-page">
    <div class="bh-bg" aria-hidden="true">
      <div class="stars" />
      <div class="disk" />
      <div class="hole" />
      <div class="glow" />
    </div>
    <div class="bh-veil" aria-hidden="true" />

    <div class="login-stage">
      <header class="brand">
        <p class="brand-tag">{{ brandCopy.loginTag }}</p>
        <h1 class="brand-name">{{ systemName }}</h1>
        <p class="brand-line">{{ brandCopy.loginLine }}</p>
      </header>

      <section class="login-card" aria-label="登录">
        <h2 class="card-title">登录</h2>
        <p class="card-sub">使用账号继续</p>
        <a-form layout="vertical" :model="form" @finish="onSubmit">
          <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
            <a-input
              v-model:value="form.username"
              size="large"
              autocomplete="username"
              placeholder="用户名"
            />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password
              v-model:value="form.password"
              size="large"
              autocomplete="current-password"
              placeholder="密码"
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" html-type="submit" size="large" block :loading="submitting">
              {{ brandCopy.loginCta }}
            </a-button>
          </a-form-item>
        </a-form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { login as apiLogin } from '@/api/auth'
import { fetchWelcomeBrand } from '@/api/welcome'
import { useUserProfileStore } from '@/stores/useUserProfileStore'
import { DEFAULT_BRAND_NAME, brandCopy } from '@/utils/brandCopy'

const route = useRoute()
const router = useRouter()
const userProfile = useUserProfileStore()
const systemName = ref(DEFAULT_BRAND_NAME)

const form = reactive({
  username: 'admin',
  password: '',
})

const submitting = ref(false)

async function onSubmit() {
  submitting.value = true
  try {
    const res = await apiLogin(form.username.trim(), form.password)
    localStorage.setItem('token', res.token)
    userProfile.saveLocalIdentity({
      userId: res.userId,
      userName: res.username,
      userDisplayName: res.displayName,
      role: res.role,
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target =
      redirect && redirect.startsWith('/') && redirect !== '/' && !redirect.startsWith('/login')
        ? redirect
        : '/chat'
    await router.replace(target)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void fetchWelcomeBrand()
    .then((b) => {
      const n = (b.systemName || '').trim()
      if (!n) return
      systemName.value = n
      document.title = n
    })
    .catch(() => { /* 未登录也可展示默认名 */ })
})
</script>

<style scoped lang="scss">
.login-page {
  position: relative;
  isolation: isolate;
  flex: 1 1 auto;
  width: 100%;
  min-width: 0;
  min-height: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  overflow: hidden;
  background: #000;
  color: #eafaff;
}

.bh-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  background: radial-gradient(ellipse at 58% 48%, #0b1220 0%, #000 62%);
}

.stars {
  position: absolute;
  inset: -10%;
  background-image:
    radial-gradient(1.5px 1.5px at 12% 18%, rgba(255, 255, 255, 0.85), transparent),
    radial-gradient(1px 1px at 28% 72%, rgba(180, 220, 255, 0.7), transparent),
    radial-gradient(1.5px 1.5px at 44% 30%, rgba(255, 255, 255, 0.75), transparent),
    radial-gradient(1px 1px at 61% 82%, rgba(255, 210, 160, 0.55), transparent),
    radial-gradient(1.5px 1.5px at 78% 22%, rgba(200, 240, 255, 0.8), transparent),
    radial-gradient(1px 1px at 88% 58%, rgba(255, 255, 255, 0.65), transparent),
    radial-gradient(1px 1px at 8% 48%, rgba(160, 210, 255, 0.55), transparent),
    radial-gradient(1.5px 1.5px at 52% 12%, rgba(255, 255, 255, 0.7), transparent);
  background-size: 100% 100%;
  opacity: 0.85;
}

.disk {
  position: absolute;
  left: 52%;
  top: 48%;
  width: min(92vw, 920px);
  height: min(34vw, 340px);
  transform: translate(-50%, -50%) rotateX(68deg);
  border-radius: 50%;
  background:
    radial-gradient(closest-side, transparent 28%, rgba(255, 180, 84, 0.15) 36%, rgba(255, 140, 40, 0.55) 48%, rgba(127, 220, 255, 0.35) 58%, transparent 72%);
  box-shadow:
    0 0 60px rgba(255, 160, 60, 0.25),
    0 0 120px rgba(80, 160, 255, 0.15);
}

.hole {
  position: absolute;
  left: 58%;
  top: 48%;
  width: min(22vw, 220px);
  height: min(22vw, 220px);
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: radial-gradient(circle at 45% 40%, #111 0%, #000 58%, #000 100%);
  box-shadow:
    0 0 0 2px rgba(127, 220, 255, 0.22),
    0 0 40px 8px rgba(0, 0, 0, 0.9),
    0 0 80px 20px rgba(255, 160, 60, 0.12);
}

.glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 62% 46%, rgba(255, 170, 70, 0.12), transparent 28%),
    radial-gradient(circle at 55% 52%, rgba(80, 180, 255, 0.1), transparent 36%);
  pointer-events: none;
}

.bh-veil {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background:
    radial-gradient(ellipse 70% 55% at 62% 48%, transparent 0%, rgba(0, 0, 0, 0.35) 55%, rgba(0, 0, 0, 0.72) 100%),
    linear-gradient(90deg, rgba(0, 0, 0, 0.55) 0%, rgba(0, 0, 0, 0.18) 42%, rgba(0, 0, 0, 0.5) 100%);
}

.login-stage {
  position: relative;
  z-index: 2;
  width: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 420px);
  align-items: center;
  gap: clamp(24px, 5vw, 64px);
  padding: clamp(28px, 6vh, 72px) clamp(20px, 5vw, 72px);
  box-sizing: border-box;
}

.brand {
  max-width: 34rem;
}

.brand-tag {
  margin: 0 0 14px;
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.72rem;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: rgba(127, 220, 255, 0.72);
}

.brand-name {
  margin: 0;
  font-family: var(--font-family-display);
  font-size: clamp(2.75rem, 7vw, 4.75rem);
  font-weight: normal;
  line-height: 1.05;
  letter-spacing: 0.04em;
  color: #f8fbff;
  text-shadow:
    0 0 40px rgba(127, 220, 255, 0.28),
    0 12px 40px rgba(0, 0, 0, 0.55);
}

.brand-line {
  margin: 18px 0 0;
  max-width: 28rem;
  font-size: clamp(0.95rem, 1.6vw, 1.125rem);
  line-height: 1.55;
  color: rgba(234, 250, 255, 0.72);
}

.login-card {
  width: 100%;
  padding: 28px 26px 22px;
  border-radius: 18px;
  background: rgba(8, 14, 24, 0.62);
  border: 1px solid rgba(127, 220, 255, 0.22);
  box-shadow:
    0 0 0 1px rgba(255, 180, 84, 0.06) inset,
    0 28px 64px rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(18px) saturate(1.15);
  -webkit-backdrop-filter: blur(18px) saturate(1.15);
}

.card-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #f8fbff;
  letter-spacing: 0.06em;
}

.card-sub {
  margin: 6px 0 22px;
  font-size: 0.875rem;
  color: rgba(234, 250, 255, 0.55);
}

.login-card :deep(.ant-form-item-label > label) {
  color: rgba(199, 232, 245, 0.88);
}

.login-card :deep(.ant-input-affix-wrapper),
.login-card :deep(.ant-input-password),
.login-card :deep(input.ant-input),
.login-card :deep(.ant-input) {
  background: rgba(12, 22, 38, 0.72) !important;
  background-color: rgba(12, 22, 38, 0.72) !important;
  border-color: rgba(127, 220, 255, 0.22) !important;
  color: #f8fbff !important;
  box-shadow: none !important;
  caret-color: #7fdcff;
}

.login-card :deep(.ant-input-affix-wrapper) {
  padding-inline: 11px;
}

.login-card :deep(.ant-input-affix-wrapper > input.ant-input),
.login-card :deep(.ant-input-password input.ant-input),
.login-card :deep(.ant-input-affix-wrapper .ant-input) {
  background: transparent !important;
  background-color: transparent !important;
  box-shadow: none !important;
  color: #f8fbff !important;
}

.login-card :deep(.ant-input-affix-wrapper:hover),
.login-card :deep(.ant-input-affix-wrapper-focused),
.login-card :deep(.ant-input-affix-wrapper:focus),
.login-card :deep(.ant-input:hover),
.login-card :deep(.ant-input:focus) {
  border-color: rgba(127, 220, 255, 0.45) !important;
  background: rgba(12, 22, 38, 0.82) !important;
}

.login-card :deep(.ant-input-affix-wrapper-focused),
.login-card :deep(.ant-input-affix-wrapper:focus-within) {
  box-shadow: 0 0 0 2px rgba(61, 214, 255, 0.18) !important;
}

.login-card :deep(.ant-input::placeholder),
.login-card :deep(input::placeholder) {
  color: rgba(234, 250, 255, 0.35) !important;
}

.login-card :deep(input.ant-input:-webkit-autofill),
.login-card :deep(input.ant-input:-webkit-autofill:hover),
.login-card :deep(input.ant-input:-webkit-autofill:focus),
.login-card :deep(.ant-input-affix-wrapper > input.ant-input:-webkit-autofill) {
  -webkit-text-fill-color: #f8fbff !important;
  caret-color: #7fdcff;
  transition: background-color 99999s ease-out 0s;
  box-shadow: 0 0 0 1000px rgba(12, 22, 38, 0.92) inset !important;
}

.login-card :deep(.ant-input-password-icon),
.login-card :deep(.anticon) {
  color: rgba(127, 220, 255, 0.65) !important;
}

.login-card :deep(.ant-btn-primary) {
  height: 44px;
  font-weight: 600;
  letter-spacing: 0.12em;
  background: linear-gradient(135deg, #3dd6ff 0%, #2a8cff 55%, #1d4ed8 100%);
  border: none;
  box-shadow: 0 10px 28px rgba(45, 140, 255, 0.35);
}

.login-card :deep(.ant-btn-primary:hover) {
  filter: brightness(1.06);
}

@media (max-width: 860px) {
  .login-stage {
    grid-template-columns: 1fr;
    align-content: end;
    padding-bottom: max(24px, env(safe-area-inset-bottom));
  }

  .brand {
    text-align: center;
    margin: 0 auto;
  }

  .brand-line {
    margin-left: auto;
    margin-right: auto;
  }

  .bh-veil {
    background:
      radial-gradient(ellipse 90% 60% at 50% 35%, transparent 0%, rgba(0, 0, 0, 0.45) 50%, rgba(0, 0, 0, 0.82) 100%),
      linear-gradient(180deg, rgba(0, 0, 0, 0.25) 0%, rgba(0, 0, 0, 0.7) 100%);
  }
}
</style>
