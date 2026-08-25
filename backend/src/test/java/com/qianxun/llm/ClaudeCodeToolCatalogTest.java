package com.qianxun.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeToolCatalogTest {

    @Test
    void shouldMapOfficialPascalCaseNames() {
        assertThat(ClaudeCodeToolCatalog.displayName("Bash")).isEqualTo("命令行");
        assertThat(ClaudeCodeToolCatalog.displayName("WebSearch")).isEqualTo("网页搜索");
        assertThat(ClaudeCodeToolCatalog.displayName("Read")).isEqualTo("读取文件");
        assertThat(ClaudeCodeToolCatalog.displayName("AskUserQuestion")).isEqualTo("向用户提问");
        assertThat(ClaudeCodeToolCatalog.iconKind("Bash")).isEqualTo("terminal");
        assertThat(ClaudeCodeToolCatalog.iconKind("WebFetch")).isEqualTo("extract");
    }

    @Test
    void shouldLookupIgnoreCase() {
        assertThat(ClaudeCodeToolCatalog.displayName("bash")).isEqualTo("命令行");
        assertThat(ClaudeCodeToolCatalog.displayName("WEBSEARCH")).isEqualTo("网页搜索");
        assertThat(ClaudeCodeToolCatalog.find("todowrite")).isNotNull();
    }

    @Test
    void seedRows_shouldCoverCoreTools() {
        assertThat(ClaudeCodeToolCatalog.allCodes())
                .contains("Bash", "Read", "Write", "Edit", "Glob", "Grep", "WebSearch", "WebFetch", "Agent", "Skill");
        assertThat(ClaudeCodeToolCatalog.seedRows()).isNotEmpty();
    }

    @Test
    void fallbackDisplayName_shouldNotUseHermesNames() {
        assertThat(ClaudeCodeToolCatalog.fallbackDisplayName("web_search")).isEqualTo("工具");
        assertThat(ClaudeCodeToolCatalog.fallbackDisplayName("WebSearch")).isEqualTo("网页搜索");
        assertThat(ClaudeCodeToolCatalog.fallbackDisplayName("mcp__server__lookup")).isEqualTo("MCP · server · lookup");
    }
}
