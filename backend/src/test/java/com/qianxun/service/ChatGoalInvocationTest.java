package com.qianxun.service;

import com.qianxun.web.dto.SessionGoalRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatGoalInvocationTest {

    @Test
    void roundTripJson_shouldClipAndRestore() {
        ChatGoalInvocation.Goal goal = ChatGoalInvocation.fromRequest(
                new SessionGoalRequest("  结案  ", "完成全部材料", "列出证据清单", "不得外传", 20));
        String json = ChatGoalInvocation.toJson(goal);
        ChatGoalInvocation.Goal back = ChatGoalInvocation.parseJson(json);
        assertThat(back.title()).isEqualTo("结案");
        assertThat(back.description()).isEqualTo("完成全部材料");
        assertThat(back.steps()).contains("证据");
        assertThat(back.constraints()).isEqualTo("不得外传");
        assertThat(back.stopAfterTurns()).isEqualTo(20);
        assertThat(back.isBlank()).isFalse();
    }

    @Test
    void parseJson_shouldTreatEmptyAsBlank() {
        assertThat(ChatGoalInvocation.parseJson("").isBlank()).isTrue();
        assertThat(ChatGoalInvocation.parseJson("not-json").isBlank()).isTrue();
        assertThat(ChatGoalInvocation.fromRequest(null).isBlank()).isTrue();
    }

    @Test
    void hermesCommand_shouldBeClaudeCodeCondition() {
        String cmd = ChatGoalInvocation.formatHermesCommand(
                new ChatGoalInvocation.Goal("调研", "形成报告", "输出 Markdown", "中文", 15));
        assertThat(cmd).startsWith("/goal ");
        assertThat(cmd).contains("调研");
        assertThat(cmd).contains("形成报告");
        assertThat(cmd).contains("验收方式：输出 Markdown");
        assertThat(cmd).contains("约束：中文");
        assertThat(cmd).contains("or stop after 15 turns");
        assertThat(cmd).doesNotContain("verify:");
        assertThat(cmd).doesNotContain("【长程目标】");
    }

    @Test
    void hermesCommand_shouldNotTreatReservedVerbAsHeadline() {
        String cmd = ChatGoalInvocation.formatHermesCommand(
                new ChatGoalInvocation.Goal("clear", "x", "", ""));
        assertThat(cmd).startsWith("/goal 完成以下目标：");
        assertThat(cmd).isNotEqualTo("/goal clear");
    }

    @Test
    void apply_kickoffShouldRewriteLastUserToHermesCommand() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "【长程目标】调研\n完成条件：形成报告")
        );
        List<Map<String, String>> out = ChatGoalInvocation.apply(
                messages, new ChatGoalInvocation.Goal("调研", "形成报告", "", ""), true);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).get("role")).isEqualTo("user");
        assertThat(out.get(0).get("content")).isEqualTo("/goal 调研：形成报告");
    }

    @Test
    void apply_laterShouldRewriteHistoricalDisplayButKeepFollowUp() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "【长程目标】目标A\n完成条件：做成"),
                Map.of("role", "assistant", "content", "好"),
                Map.of("role", "user", "content", "继续")
        );
        List<Map<String, String>> out = ChatGoalInvocation.apply(
                messages, new ChatGoalInvocation.Goal("目标A", "做成", "", ""), false);
        assertThat(out.get(0).get("content")).startsWith("/goal ");
        assertThat(out.get(0).get("content")).contains("做成");
        assertThat(out.get(2).get("content")).isEqualTo("继续");
        assertThat(out.stream().noneMatch(m -> "system".equals(m.get("role")))).isTrue();
    }

    @Test
    void applyClear_shouldRewriteLastUser() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "请清除当前长程目标，并继续普通对话。")
        );
        List<Map<String, String>> out = ChatGoalInvocation.applyClear(messages);
        assertThat(out.get(0).get("content")).isEqualTo("/goal clear");
    }

    @Test
    void apply_blankGoalShouldLeaveMessages() {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "hi"));
        assertThat(ChatGoalInvocation.apply(messages, new ChatGoalInvocation.Goal("", "", "", ""), true))
                .isEqualTo(messages);
    }

    @Test
    void applyClear_emptyShouldInsertCommand() {
        assertThat(ChatGoalInvocation.applyClear(List.of()).get(0).get("content"))
                .isEqualTo(ChatGoalInvocation.HERMES_CLEAR_COMMAND);
    }

    @Test
    void formatUserVisible_shouldBeChinese() {
        String text = ChatGoalInvocation.formatUserVisible(
                new ChatGoalInvocation.Goal("调研", "形成报告", "贴出目录", "", 10));
        assertThat(text).contains("【长程目标】调研").contains("完成条件").contains("验收方式").contains("轮次上限：10");
        assertThat(text).doesNotContain("分步执行");
    }
}
