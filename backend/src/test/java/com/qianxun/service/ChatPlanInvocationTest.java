package com.qianxun.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPlanInvocationTest {

    @Test
    void defaultTask_shouldFillCreateAndExecute() {
        assertThat(ChatPlanInvocation.defaultTaskForSkill("plan", ""))
                .isEqualTo(ChatPlanInvocation.DEFAULT_CREATE_TASK);
        assertThat(ChatPlanInvocation.defaultTaskForSkill("plan", "实现登录"))
                .isEqualTo("实现登录");
        assertThat(ChatPlanInvocation.defaultTaskForSkill("subagent-driven-development", "  "))
                .isEqualTo(ChatPlanInvocation.DEFAULT_EXECUTE_TASK);
        assertThat(ChatPlanInvocation.defaultTaskForSkill(
                "subagent-driven-development", ChatPlanInvocation.LOCAL_EXECUTE_DISPLAY))
                .isEqualTo(ChatPlanInvocation.DEFAULT_EXECUTE_TASK);
    }

    @Test
    void isPlanRelated_shouldRecognizeBothSkills() {
        assertThat(ChatPlanInvocation.isPlanRelated("plan")).isTrue();
        assertThat(ChatPlanInvocation.isPlanRelated("subagent-driven-development")).isTrue();
        assertThat(ChatPlanInvocation.isPlanRelated("llm-wiki")).isFalse();
    }
}
