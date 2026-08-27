package com.qianxun.llm;

import com.qianxun.service.ChatDashboardTurn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeChatClientTest {

    @Test
    void buildPrompt_shouldUseUserTextWhenNoSlash() {
        ChatDashboardTurn.Plan plan = new ChatDashboardTurn.Plan(
                null, false, "帮我写周报", List.of(Map.of("role", "user", "content", "hi")));
        assertThat(ClaudeCodeChatClient.buildPrompt(plan, "")).isEqualTo("帮我写周报");
    }

    @Test
    void buildPrompt_shouldPassThroughGoalSlash() {
        ChatDashboardTurn.Plan plan = new ChatDashboardTurn.Plan(
                "/goal 完成季度复盘", true, "【长程目标】完成季度复盘", List.of());
        String prompt = ClaudeCodeChatClient.buildPrompt(plan, "");
        assertThat(prompt).isEqualTo("/goal 完成季度复盘");
    }

    @Test
    void buildPrompt_shouldAppendNonDisplayUserAfterGoal() {
        ChatDashboardTurn.Plan plan = new ChatDashboardTurn.Plan(
                "/goal 完成季度复盘", true, "先列提纲", List.of());
        String prompt = ClaudeCodeChatClient.buildPrompt(plan, "");
        assertThat(prompt).isEqualTo("/goal 完成季度复盘\n\n先列提纲");
    }

    @Test
    void buildPrompt_shouldPassThroughGoalClear() {
        ChatDashboardTurn.Plan plan = new ChatDashboardTurn.Plan(
                "/goal clear", false, "然后闲聊", List.of());
        assertThat(ClaudeCodeChatClient.buildPrompt(plan, "")).isEqualTo("/goal clear");
    }

    @Test
    void buildPrompt_shouldRewriteAgentsSlash() {
        ChatDashboardTurn.Plan plan = new ChatDashboardTurn.Plan("/agents", false, "", List.of());
        assertThat(ClaudeCodeChatClient.buildPrompt(plan, "")).contains("子智能体");
    }

    @Test
    void buildPrompt_shouldReturnEmptyForNullPlan() {
        assertThat(ClaudeCodeChatClient.buildPrompt(null, "resume")).isEmpty();
    }
}
