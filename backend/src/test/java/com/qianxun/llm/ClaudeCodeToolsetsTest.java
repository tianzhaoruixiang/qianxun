package com.qianxun.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeToolsetsTest {

    @Test
    void catalog_shouldOnlyContainClaudeCodeToolsets() {
        assertThat(ClaudeCodeToolsets.CATALOG.stream().map(ClaudeCodeToolsets.Def::name))
                .containsExactly("web", "file", "terminal", "code_execution", "delegation", "skills", "todo", "kanban", "plan")
                .doesNotContain("memory", "session_search", "browser", "discord");
        assertThat(ClaudeCodeToolsets.DEFAULT_ENABLED)
                .containsExactlyElementsOf(
                        ClaudeCodeToolsets.CATALOG.stream().map(ClaudeCodeToolsets.Def::name).toList());
        assertThat(ClaudeCodeToolsets.isKnown("skills")).isTrue();
        assertThat(ClaudeCodeToolsets.isKnown("plan")).isTrue();
        assertThat(ClaudeCodeToolsets.isKnown("browser")).isFalse();
        assertThat(ClaudeCodeToolsets.isKnown("memory")).isFalse();
        assertThat(ClaudeCodeToolsets.CATALOG)
                .allMatch(d -> !d.claudeTools().isEmpty());
    }
}
