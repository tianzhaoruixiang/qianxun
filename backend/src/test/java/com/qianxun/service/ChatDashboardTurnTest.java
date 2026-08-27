package com.qianxun.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatDashboardTurnTest {

    @Test
    void plan_shouldUseNativeGoalSlashWithoutRewritingUser() {
        List<Map<String, String>> stored = List.of(
                Map.of("role", "user", "content", "先调研竞品"),
                Map.of("role", "assistant", "content", "好的"),
                Map.of("role", "user", "content", "开始")
        );
        ChatGoalInvocation.Goal goal = new ChatGoalInvocation.Goal("调研", "形成报告", "1. 收集", "");
        ChatDashboardTurn.Plan plan = ChatDashboardTurn.plan(false, true, goal, stored, stored);
        assertThat(plan.hasSlash()).isTrue();
        assertThat(plan.expectSendThenPrompt()).isTrue();
        assertThat(plan.slashCommand()).startsWith("/goal ");
        assertThat(plan.slashCommand()).contains("调研");
        assertThat(plan.slashCommand()).contains("形成报告");
        assertThat(plan.slashCommand()).doesNotContain("verify:");
        assertThat(plan.promptText()).isEqualTo("开始");
        assertThat(plan.seedHistory()).extracting(m -> m.get("content"))
                .containsExactly("先调研竞品", "好的");
    }

    @Test
    void plan_shouldClearWithSlashAndKeepOrdinaryTurnsUnrewritten() {
        List<Map<String, String>> stored = List.of(Map.of("role", "user", "content", "你好"));
        ChatDashboardTurn.Plan clear = ChatDashboardTurn.plan(
                true, false, ChatGoalInvocation.fromRequest(null), stored, stored);
        assertThat(clear.slashCommand()).isEqualTo("/goal clear");
        assertThat(clear.expectSendThenPrompt()).isFalse();

        ChatDashboardTurn.Plan ordinary = ChatDashboardTurn.plan(
                false, false, ChatGoalInvocation.fromRequest(null), stored, stored);
        assertThat(ordinary.hasSlash()).isFalse();
        assertThat(ordinary.promptText()).isEqualTo("你好");
    }

    @Test
    void plan_shouldUseNativeSkillSlashWithoutInjectingSkillMd() {
        List<Map<String, String>> stored = List.of(
                Map.of("role", "user", "content", "上一轮"),
                Map.of("role", "assistant", "content", "好"),
                Map.of("role", "user", "content", "写一篇摘要")
        );
        ChatDashboardTurn.Plan plan = ChatDashboardTurn.plan(
                false, false, ChatGoalInvocation.fromRequest(null), "llm-wiki", stored, stored);
        assertThat(plan.hasSlash()).isTrue();
        assertThat(plan.expectSendThenPrompt()).isFalse();
        assertThat(plan.slashCommand()).isEqualTo("/llm-wiki 写一篇摘要");
        assertThat(plan.promptText()).isEqualTo("写一篇摘要");
        assertThat(plan.seedHistory()).extracting(m -> m.get("content"))
                .containsExactly("上一轮", "好");
    }

    @Test
    void plan_goalKickoffTakesPrecedenceOverSkill() {
        List<Map<String, String>> stored = List.of(Map.of("role", "user", "content", "开始"));
        ChatGoalInvocation.Goal goal = new ChatGoalInvocation.Goal("调研", "", "", "");
        ChatDashboardTurn.Plan plan = ChatDashboardTurn.plan(
                false, true, goal, "llm-wiki", stored, stored);
        assertThat(plan.slashCommand()).startsWith("/goal 调研");
        assertThat(plan.slashCommand()).doesNotContain("llm-wiki");
    }

    @Test
    void plan_shouldUseNativeAgentsStatusSlash() {
        List<Map<String, String>> stored = List.of(Map.of("role", "user", "content", "查看"));
        ChatDashboardTurn.Plan plan = ChatDashboardTurn.plan(
                false, false, ChatGoalInvocation.fromRequest(null), null, true, stored, stored);
        assertThat(plan.hasSlash()).isTrue();
        assertThat(plan.expectSendThenPrompt()).isFalse();
        assertThat(plan.slashCommand()).isEqualTo("/agents");
    }

    @Test
    void plan_agentsStatusTakesPrecedenceOverSkill() {
        List<Map<String, String>> stored = List.of(Map.of("role", "user", "content", "x"));
        ChatDashboardTurn.Plan plan = ChatDashboardTurn.plan(
                false, false, ChatGoalInvocation.fromRequest(null), "llm-wiki", true, stored, stored);
        assertThat(plan.slashCommand()).isEqualTo("/agents");
    }

    @Test
    void extraSystemText_shouldPrependThisTurnFileContext() {
        List<Map<String, String>> stored = List.of(Map.of("role", "user", "content", "总结附件"));
        List<Map<String, String>> turn = List.of(
                Map.of("role", "system", "content", "用户本轮附上了文档"),
                Map.of("role", "user", "content", "总结附件")
        );
        ChatDashboardTurn.Plan plan = ChatDashboardTurn.plan(
                false, false, ChatGoalInvocation.fromRequest(null), stored, turn);
        assertThat(plan.promptText()).startsWith("用户本轮附上了文档");
        assertThat(plan.promptText()).contains("总结附件");
        assertThat(plan.seedHistory()).isEmpty();
    }

    @Test
    void plan_skillSlash_shouldCarryFileContextInCommandArg() {
        List<Map<String, String>> stored = List.of(Map.of("role", "user", "content", "总结附件"));
        List<Map<String, String>> turn = List.of(
                Map.of("role", "system", "content", "用户本轮附上了文档"),
                Map.of("role", "user", "content", "总结附件")
        );
        ChatDashboardTurn.Plan plan = ChatDashboardTurn.plan(
                false, false, ChatGoalInvocation.fromRequest(null), "llm_wiki", stored, turn);
        assertThat(plan.slashCommand()).startsWith("/llm-wiki ");
        assertThat(plan.slashCommand()).contains("用户本轮附上了文档");
        assertThat(plan.slashCommand()).contains("总结附件");
    }
}
