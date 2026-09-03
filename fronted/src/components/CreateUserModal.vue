<template>
  <a-modal
    v-model:open="openProxy"
    title="创建用户"
    ok-text="创建"
    cancel-text="取消"
    :confirm-loading="submitting"
    destroy-on-close
    @ok="onSubmit"
  >
    <p class="hint">创建后的功能用户可登录使用千寻，但不能再创建其他账号。</p>
    <a-form layout="vertical" :model="form">
      <a-form-item label="用户名" required>
        <a-input v-model:value="form.username" autocomplete="off" maxlength="64" placeholder="登录名" />
      </a-form-item>
      <a-form-item label="显示名">
        <a-input v-model:value="form.displayName" autocomplete="off" maxlength="128" placeholder="可选，默认与用户名相同" />
      </a-form-item>
      <a-form-item label="密码" required>
        <a-input-password v-model:value="form.password" autocomplete="new-password" maxlength="128" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { createFunctionalUser } from '@/api/user'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

const openProxy = computed({
  get: () => props.open,
  set: (v: boolean) => emit('update:open', v),
})

const submitting = ref(false)
const form = reactive({
  username: '',
  displayName: '',
  password: '',
})

watch(
  () => props.open,
  (open) => {
    if (open) {
      form.username = ''
      form.displayName = ''
      form.password = ''
    }
  },
)

async function onSubmit() {
  const username = form.username.trim()
  const password = form.password
  if (!username) {
    message.warning('请输入用户名')
    return
  }
  if (!password.trim()) {
    message.warning('请输入密码')
    return
  }
  submitting.value = true
  try {
    await createFunctionalUser({
      username,
      password,
      displayName: form.displayName.trim() || undefined,
    })
    message.success('已创建功能用户')
    emit('update:open', false)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.hint {
  margin: 0 0 12px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
</style>
