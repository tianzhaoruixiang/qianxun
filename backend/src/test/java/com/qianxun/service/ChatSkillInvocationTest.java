package com.qianxun.service;

import com.qianxun.llm.HermesAgentClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSkillInvocationTest {

    @Test
    void formatHermesCommand_shouldMatchOfficialSkillSlash() {
        assertThat(ChatSkillInvocation.formatHermesCommand("llm-wiki", "写摘要"))
                .isEqualTo("/llm-wiki 写摘要");
        assertThat(ChatSkillInvocation.formatHermesCommand("Case_Brief", "  "))
                .isEqualTo("/case-brief");
        assertThat(ChatSkillInvocation.toSlashSlug("My Skill++")).isEqualTo("my-skill");
        assertThat(ChatSkillInvocation.formatHermesCommand("  ", "x")).isEmpty();
    }

    @Test
    void findEnabled_shouldIgnoreDisabledAndMatchCaseInsensitive() {
        List<HermesAgentClient.SkillInfo> skills = List.of(
                new HermesAgentClient.SkillInfo("brief", "摘要", "doc", false, "bundled"),
                new HermesAgentClient.SkillInfo("Case-Brief", "案件摘要", "doc", true, "agent")
        );
        assertThat(ChatSkillInvocation.findEnabled(skills, "case-brief")).isNotNull();
        assertThat(ChatSkillInvocation.findEnabled(skills, "brief")).isNull();
        assertThat(ChatSkillInvocation.exists(skills, "brief")).isTrue();
        assertThat(ChatSkillInvocation.matchesName("smart-charts-600", "smart_charts_600")).isTrue();
        assertThat(ChatSkillInvocation.toSlashSlug("smart-charts-600")).isEqualTo("smart-charts-600");
    }

    @Test
    void apply_shouldInjectSystemHintAndPrefixLastUser() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "keep"),
                Map.of("role", "user", "content", "上一轮"),
                Map.of("role", "assistant", "content", "好"),
                Map.of("role", "user", "content", "写周报")
        );
        List<Map<String, String>> out = ChatSkillInvocation.apply(messages, "weekly-report", "# 周报技能\n用表格");
        assertThat(out.get(0).get("role")).isEqualTo("system");
        assertThat(out.get(0).get("content")).contains("weekly-report").contains("周报技能");
        assertThat(out.get(out.size() - 1).get("content"))
                .isEqualTo("请使用技能「weekly-report」完成以下任务：\n写周报");
        assertThat(out.stream().filter(m -> "user".equals(m.get("role"))).count()).isEqualTo(2);
        assertThat(out.get(2).get("content")).isEqualTo("上一轮");
    }

    @Test
    void prefixUserTask_shouldFallbackWhenEmpty() {
        assertThat(ChatSkillInvocation.prefixUserTask("x", "  "))
                .contains("请按该技能执行");
    }

    @Test
    void refuseMessage_shouldDistinguishDisabled() {
        assertThat(ChatSkillInvocation.refuseMessage(true, "demo")).contains("未启用");
        assertThat(ChatSkillInvocation.refuseMessage(false, "demo")).contains("不存在");
    }
}
