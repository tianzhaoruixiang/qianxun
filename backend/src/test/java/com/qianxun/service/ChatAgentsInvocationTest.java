package com.qianxun.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAgentsInvocationTest {

    @Test
    void formatHermesCommand_shouldBeOfficialAgentsSlash() {
        assertThat(ChatAgentsInvocation.formatHermesCommand()).isEqualTo("/agents");
    }

    @Test
    void looksLikeCommand_shouldRecognizeAgentsTasksAndLocalDisplay() {
        assertThat(ChatAgentsInvocation.looksLikeCommand("/agents")).isTrue();
        assertThat(ChatAgentsInvocation.looksLikeCommand("/tasks")).isTrue();
        assertThat(ChatAgentsInvocation.looksLikeCommand("/task")).isTrue();
        assertThat(ChatAgentsInvocation.looksLikeCommand("/task status")).isTrue();
        assertThat(ChatAgentsInvocation.looksLikeCommand(ChatAgentsInvocation.LOCAL_DISPLAY)).isTrue();
        assertThat(ChatAgentsInvocation.looksLikeCommand("普通提问")).isFalse();
    }

    @Test
    void extractStatusQuery_shouldKeepLogArgs() {
        assertThat(ChatAgentsInvocation.extractStatusQuery("/task")).isEmpty();
        assertThat(ChatAgentsInvocation.extractStatusQuery("/task log deleg_cfb92b95"))
                .isEqualTo("log deleg_cfb92b95");
        assertThat(ChatAgentsInvocation.extractStatusQuery("/tasks deleg_cfb92b95 1"))
                .isEqualTo("deleg_cfb92b95 1");
        assertThat(ChatAgentsInvocation.displayContent("log deleg_cfb92b95"))
                .contains("log deleg_cfb92b95");
    }

    @Test
    void unavailableMessage_shouldMentionRunner() {
        assertThat(ChatAgentsInvocation.unavailableMessage()).contains("智能体运行器");
    }
}
