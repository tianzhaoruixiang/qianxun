<template>
  <div class="welcome-section">
    <p class="ai-disclaimer">{{ disclaimerText }}</p>

    <div class="avatar-wrapper">
      <AgentAvatar
        class="avatar"
        :group-key="chatAvatar.groupKey"
        :label="chatAvatar.label"
        :icon="chatAvatar.icon"
        size="xl"
      />
    </div>

    <h2 class="greeting">{{ greetingText }}</h2>

    <p class="description">
      <img :src="logoImg" alt="logo" class="icon-dot" />
      {{ capabilityText }}
    </p>

    <template v-if="presetPrompts.length">
      <p class="recommend-label">{{ presetLabel }}</p>
      <div class="questions-grid">
        <div
          v-for="(question, index) in presetPrompts"
          :key="'preset-' + index"
          class="question-card"
          :style="{ animationDelay: `${index * 80}ms` }"
          @click="handleQuestionClick(question)"
        >
          <span class="question-text">{{ question }}</span>
        </div>
      </div>
    </template>

    <template v-if="showDefaultSuggestedQuestions">
      <p class="recommend-label">{{ recommendLabelText }}</p>

      <div class="questions-grid">
        <div
          v-for="(question, index) in questions"
          :key="question.id || index"
          class="question-card"
          :style="{ animationDelay: `${index * 80}ms` }"
          @click="handleQuestionClick(question.text)"
        >
          <span class="question-text">{{ question.text }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useChatStore } from '@/stores/useChatStore'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { useAgentContextStore } from '@/stores/useAgentContextStore'
import AgentAvatar from '@/components/AgentAvatar.vue'
import { useChatAgentAvatar } from '@/composables/useChatAgentAvatar'
import logoImg from '@/assets/images/logo.png'
import { brandCopy, DEFAULT_BRAND_NAME } from '@/utils/brandCopy'
import { useSystemName } from '@/utils/systemName'

const chatStore = useChatStore()
const bootstrap = useBootstrapStore()
const agentContext = useAgentContextStore()
const chatAvatar = useChatAgentAvatar()
const systemName = useSystemName()

onMounted(() => {
  void bootstrap.ensureLoaded()
})

/** 与品牌无关的通用能力描述（避免与已选智能体主标题冲突） */
const NEUTRAL_CAPABILITY = brandCopy.welcomeCapabilityNeutral

const disclaimerText = computed(() => {
  const raw = (bootstrap.disclaimer || brandCopy.welcomeDisclaimer).trim()
  return raw
    .replace(/AI\s*大模型/g, 'AI 智能体')
    .replace(/内容由AI大模型生成/g, '内容由 AI 智能体生成')
})

const greetingText = computed(() => {
  const title = agentContext.activeAgent?.welcomeTitle?.trim()
  if (title) return title
  const name = agentContext.activeAgent?.name?.trim()
  if (name) return `你好，我是 ${name}`
  const greeting = bootstrap.greeting || brandCopy.welcomeGreeting
  const officerName = systemName.value
  return greeting
    .replaceAll('千寻问答助手', officerName)
    .replaceAll('千寻', officerName)
    .replaceAll('数字干警', officerName)
    .replaceAll(DEFAULT_BRAND_NAME, officerName)
})

const capabilityText = computed(() => {
  const intro = agentContext.activeAgent?.welcomeIntro?.trim()
  if (intro) return intro
  // 已从智能体页选中智能体：不要用全局 capability，否则与主标题身份矛盾
  if (agentContext.activeAgent) {
    const cardDesc = agentContext.activeAgent.description?.trim()
    if (cardDesc) return cardDesc
    return NEUTRAL_CAPABILITY
  }
  return bootstrap.capability || brandCopy.welcomeCapability
})
const recommendLabelText = computed(() => bootstrap.recommendLabel || brandCopy.welcomeRecommend)

/** 已展示智能体/数智干警预置对话时，不再叠一层全局推荐问法 */
const showDefaultSuggestedQuestions = computed(() => !agentContext.activeAgent && !officerPresetPrompts.value.length)

const officerPresetPrompts = computed(() => {
  if (agentContext.activeAgent) return []
  return [bootstrap.presetChat1, bootstrap.presetChat2, bootstrap.presetChat3]
    .map((s) => s?.trim())
    .filter((s): s is string => Boolean(s))
})

const agentPresetPrompts = computed(() => {
  const a = agentContext.activeAgent
  if (!a) return []
  return [a.presetChat1, a.presetChat2, a.presetChat3]
    .map((s) => s?.trim())
    .filter((s): s is string => Boolean(s))
})

const presetPrompts = computed(() =>
  agentPresetPrompts.value.length ? agentPresetPrompts.value : officerPresetPrompts.value,
)

const presetLabel = computed(() =>
  presetPrompts.value.length ? '快速开始' : '',
)

const questions = computed(() =>
  bootstrap.suggestedQuestions.length ? bootstrap.suggestedQuestions : [],
)

function handleQuestionClick(text: string) {
  chatStore.sendMessage(text)
}
</script>

<style scoped lang="scss">
@import '@/styles/variables.scss';
@import '@/styles/mixins.scss';

.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: fade-in-up 0.4s ease-out;

  .ai-disclaimer {
    margin: 0 0 48px;
    font-size: var(--font-size-md);
    color: var(--text-muted);
    letter-spacing: 0.5px;
  }

  .avatar-wrapper {
    margin-bottom: 20px;
    height: 96px;
    display: flex;
    align-items: center;
    justify-content: center;

    .avatar {
      width: 96px;
      height: 96px;
    }
  }

  .greeting {
    margin: 0 0 40px;
    font-size: var(--font-size-display);
    background: linear-gradient(64deg, #3861f4 -7%, #36abff 103%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    text-fill-color: transparent;
    letter-spacing: 1px;
    font-family: var(--font-family-display);
    font-weight: normal;
  }

  .description {
    margin: 0 0 28px;
    font-size: var(--font-size-lg);
    color: var(--text-secondary);
    line-height: var(--line-height-relaxed);
    max-width: 562px;
    text-align: center;

    .icon-dot {
      display: inline-block;
      width: 23px;
      margin-right: 4px;
      vertical-align: middle;
    }
  }

  .recommend-label {
    margin: 0 0 16px;
    font-size: var(--font-size-lg);
    color: var(--text-muted);
    width: 800px;
  }

  .questions-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    width: 100%;
    max-width: 800px;

    .question-card {
      animation: fade-in-up 0.4s ease-out both;
      padding: 18px 20px;
      font-size: var(--font-size-lg);
      color: var(--text-primary);
      line-height: var(--line-height-normal);
      letter-spacing: var(--letter-spacing-tight);
      cursor: pointer;
      transition:
        transform 0.28s cubic-bezier(0.22, 1, 0.36, 1),
        box-shadow 0.28s cubic-bezier(0.22, 1, 0.36, 1),
        border-color 0.28s ease;
      .question-text {
        display: -webkit-box;
        -webkit-line-clamp: 3;
        -webkit-box-orient: vertical;
        overflow: hidden;
        width: 100%;
        height: 100%;
      }
      position: relative;
      height: 122px;
      border: 1px solid var(--card-border, rgba(0, 0, 0, 0.04));
      border-radius: var(--radius-md, 16px);
      box-shadow: var(--card-shadow);
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.72) 0%, rgba(255, 255, 255, 0.48) 100%),
        url(@/assets/images/questionBg.png) no-repeat center / cover;
      backdrop-filter: blur(12px) saturate(1.2);
      -webkit-backdrop-filter: blur(12px) saturate(1.2);

      &:hover {
        transform: translateY(-2px);
        border-color: var(--card-border-hover, rgba(0, 0, 0, 0.08));
        box-shadow: var(--card-shadow-hover);
      }

      &:active {
        transform: translateY(0);
        box-shadow: var(--card-shadow-pressed, var(--shadow-sm));
      }
    }
  }
}
</style>
