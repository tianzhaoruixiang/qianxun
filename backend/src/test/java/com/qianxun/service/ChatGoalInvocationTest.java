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
                new SessionGoalRequest("  结案  ", "完成全部材料", "1.收集\n2.撰写", "不得外传"));
        String json = ChatGoalInvocation.toJson(goal);
        ChatGoalInvocation.Goal back = ChatGoalInvocation.parseJson(json);
        assertThat(back.title()).isEqualTo("结案");
        assertThat(back.description()).isEqualTo("完成全部材料");
        assertThat(back.steps()).contains("撰写");
        assertThat(back.constraints()).isEqualTo("不得外传");
        assertThat(back.isBlank()).isFalse();
    }

    @Test
    void parseJson_shouldTreatEmptyAsBlank() {
        assertThat(ChatGoalInvocation.parseJson("").isBlank()).isTrue();
        assertThat(ChatGoalInvocation.parseJson("not-json").isBlank()).isTrue();
        assertThat(ChatGoalInvocation.fromRequest(null).isBlank()).isTrue();
    }

    @Test
    void hermesCommand_shouldStartWithSlashGoalAndContractFields() {
        String cmd = ChatGoalInvocation.formatHermesCommand(
                new ChatGoalInvocation.Goal("调研", "形成报告", "分三步", "中文"));
        assertThat(cmd).startsWith("/goal 调研");
        assertThat(cmd).contains("分三步");
        assertThat(cmd).contains("verify: 形成报告");
        assertThat(cmd).contains("constraints: 中文");
        assertThat(cmd).doesNotContain("【长程目标】");
        assertThat(cmd).doesNotContain("Hermes");
    }

    @Test
    void hermesCommand_shouldNotTreatReservedVerbAsHeadline() {
        String cmd = ChatGoalInvocation.formatHermesCommand(
                new ChatGoalInvocation.Goal("clear", "x", "", ""));
        assertThat(cmd).startsWith("/goal 长程任务 clear");
        assertThat(cmd).isNotEqualTo("/goal clear");
    }

    @Test
    void apply_kickoffShouldRewriteLastUserToHermesCommand() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "【长程目标】调研\n成功标准：形成报告")
        );
        List<Map<String, String>> out = ChatGoalInvocation.apply(
                messages, new ChatGoalInvocation.Goal("调研", "形成报告", "", ""), true);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).get("role")).isEqualTo("user");
        assertThat(out.get(0).get("content")).isEqualTo("/goal 调研\nverify: 形成报告");
    }

    @Test
    void apply_laterShouldRewriteHistoricalDisplayButKeepFollowUp() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "【长程目标】目标A\n请按该目标分步执行，在达成成功标准前不要停止。"),
                Map.of("role", "assistant", "content", "好"),
                Map.of("role", "user", "content", "继续")
        );
        List<Map<String, String>> out = ChatGoalInvocation.apply(
                messages, new ChatGoalInvocation.Goal("目标A", "做成", "", ""), false);
        assertThat(out.get(0).get("content")).startsWith("/goal 目标A");
        assertThat(out.get(0).get("content")).contains("verify: 做成");
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
                new ChatGoalInvocation.Goal("调研", "形成报告", "分三步", ""));
        assertThat(text).contains("【长程目标】调研").contains("成功标准").contains("分步执行");
    }
}
