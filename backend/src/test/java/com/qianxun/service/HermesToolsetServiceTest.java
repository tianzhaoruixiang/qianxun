package com.qianxun.service;

import com.qianxun.config.QianxunProperties;
import com.qianxun.context.UserContext;
import com.qianxun.llm.ClaudeCodeToolsets;
import com.qianxun.llm.HermesAgentClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HermesToolsetServiceTest {

    private static HermesToolsetService service(HermesAgentClient hermes) {
        QianxunProperties props = new QianxunProperties();
        props.getClaude().setAppendNoMcp(true);
        return new HermesToolsetService(hermes, props);
    }

    @Test
    void toView_shouldMapChineseToolNames() {
        HermesAgentClient.ToolsetInfo raw = new HermesAgentClient.ToolsetInfo(
                "web", "Web", "search the web", "cli", "CLI", true, true,
                List.of("unknown_hermes_tool", "web_extract")
        );
        HermesToolsetService.ToolsetView view = HermesToolsetService.toView(raw);
        assertThat(view.label()).isEqualTo("Web");
        assertThat(view.tools()).extracting(HermesToolsetService.ToolItem::displayName)
                .containsOnly("工具");
        assertThat(view.tools()).extracting(HermesToolsetService.ToolItem::enabled)
                .containsOnly(true);
    }

    @Test
    void toView_shouldMapClaudePascalCaseToolNames() {
        HermesAgentClient.ToolsetInfo raw = new HermesAgentClient.ToolsetInfo(
                "web", "Web", "search the web", "cli", "CLI", true, true,
                List.of("WebSearch", "WebFetch", "Bash")
        );
        HermesToolsetService.ToolsetView view = HermesToolsetService.toView(raw);
        assertThat(view.tools()).extracting(HermesToolsetService.ToolItem::displayName)
                .contains("网页搜索", "抓取网页", "命令行");
        assertThat(view.tools()).extracting(HermesToolsetService.ToolItem::iconKind)
                .contains("search", "extract", "terminal");
    }

    @Test
    void toView_shouldUseChatEnabledNotDashboardFlag() {
        HermesToolsetService.ToolsetView view = HermesToolsetService.toView(info("browser", "cli", true), false);
        assertThat(view.enabled()).isFalse();
        assertThat(view.tools()).extracting(HermesToolsetService.ToolItem::enabled).containsOnly(false);
    }

    @Test
    void chatEnabledSet_shouldDropSentinelAndMatchIgnoreCase() {
        Set<String> on = HermesToolsetService.chatEnabledSet(List.of("Web", "file", "no_mcp"));
        assertThat(on).containsExactly("web", "file");
        assertThat(HermesToolsetService.chatEnabled("web", on)).isTrue();
        assertThat(HermesToolsetService.chatEnabled("browser", on)).isFalse();
        assertThat(HermesToolsetService.chatEnabled("no_mcp", on)).isFalse();
    }

    @Test
    void list_shouldUseCliEnabledAndSkipComposites() {
        HermesAgentClient hermes = mock(HermesAgentClient.class);
        when(hermes.listToolsets(eq(UserContext.DEFAULT_USER_ID), eq("worker"))).thenReturn(List.of(
                info("web", "cli", true),
                info("browser", "cli", false),
                info("hermes-cli", "cli", true)
        ));
        HermesToolsetService svc = service(hermes);
        List<HermesToolsetService.ToolsetView> views = svc.list("worker");
        assertThat(views).extracting(HermesToolsetService.ToolsetView::name)
                .containsExactlyElementsOf(ClaudeCodeToolsets.CATALOG.stream().map(ClaudeCodeToolsets.Def::name).toList());
        assertThat(views).extracting(HermesToolsetService.ToolsetView::name)
                .doesNotContain("browser", "hermes-cli", "memory", "session_search");
        assertThat(views).filteredOn(v -> "web".equals(v.name()))
                .first().extracting(HermesToolsetService.ToolsetView::enabled).isEqualTo(true);
        assertThat(views).filteredOn(v -> "file".equals(v.name()))
                .first().extracting(HermesToolsetService.ToolsetView::enabled).isEqualTo(true);
    }

    @Test
    void planChatGateway_shouldUseBaselineInsteadOfCopyingCliToolsets() {
        List<HermesAgentClient.ToolsetInfo> listed = List.of(
                info("web", "cli", true),
                info("file", "cli", true),
                info("browser", "cli", false),
                info("discord", "discord", false),
                info("hermes-cli", "cli", true),
                info("stt", "cli", true)
        );
        HermesToolsetService.ChatToolsetPlan plan = HermesToolsetService.planChatGateway(listed);
        assertThat(plan.enabled()).containsExactlyElementsOf(ClaudeCodeToolsets.DEFAULT_ENABLED);
        assertThat(plan.disabled()).doesNotContainAnyElementsOf(ClaudeCodeToolsets.DEFAULT_ENABLED);
        assertThat(plan.known()).contains("web", "file");
        assertThat(plan.known()).doesNotContain("browser", "discord", "hermes-cli", "stt");
        assertThat(plan.disabled()).doesNotContain("discord", "browser");
        assertThat(HermesToolsetService.apiServerConfigList(plan.enabled(), true))
                .containsExactlyElementsOf(defaultPlusNoMcp());
    }

    @Test
    void planChatGateway_shouldEnableAllClaudeCodeToolsets() {
        HermesToolsetService.ChatToolsetPlan plan = HermesToolsetService.planChatGateway(List.of(
                info("web", "cli", true),
                info("file", "cli", true),
                info("terminal", "cli", true),
                info("code_execution", "cli", true),
                info("delegation", "cli", true),
                info("skills", "cli", true),
                info("todo", "cli", true),
                info("kanban", "cli", true),
                info("plan", "cli", true)
        ));
        assertThat(plan.enabled()).containsExactlyElementsOf(ClaudeCodeToolsets.DEFAULT_ENABLED);
        assertThat(plan.enabled()).contains("skills", "plan", "kanban", "todo");
        assertThat(plan.enabled()).doesNotContain("memory", "session_search", "browser");
        assertThat(plan.disabled()).doesNotContain(
                "skills", "plan", "kanban",
                "web", "file", "terminal", "code_execution", "delegation");
        assertThat(HermesToolsetService.apiServerConfigList(plan.enabled(), true))
                .containsExactlyElementsOf(defaultPlusNoMcp());
    }

    @Test
    void planChatGateway_shouldAlwaysKeepFileInSafeBaseline() {
        HermesToolsetService.ChatToolsetPlan missing = HermesToolsetService.planChatGateway(List.of(
                info("web", "cli", true)
        ));
        assertThat(missing.enabled()).containsExactlyElementsOf(ClaudeCodeToolsets.DEFAULT_ENABLED);
        assertThat(missing.disabled()).doesNotContain("file", "web");

        HermesToolsetService.ChatToolsetPlan off = HermesToolsetService.planChatGateway(List.of(
                info("file", "cli", false),
                info("web", "cli", true)
        ));
        assertThat(off.enabled()).contains("web", "file", "skills");
        assertThat(off.disabled()).doesNotContain("file", "web", "skills");
    }

    @Test
    void planChatGateway_longHorizonShouldForceTodoAndFile() {
        HermesToolsetService.ChatToolsetPlan plan = HermesToolsetService.planChatGateway(List.of(
                info("web", "cli", true),
                info("file", "cli", false),
                info("todo", "cli", false),
                info("kanban", "cli", true)
        ), true);
        assertThat(plan.enabled()).contains(
                "web", "file", "terminal", "code_execution", "delegation", "todo", "kanban", "skills");
        assertThat(plan.disabled()).doesNotContain("file", "todo", "kanban", "skills");
    }

    @Test
    void mergeLongHorizon_shouldKeepApiServerAndAddTaskToolsets() {
        HermesToolsetService.ChatToolsetPlan plan = HermesToolsetService.mergeLongHorizon(
                List.of("web", "no_mcp"),
                List.of(info("web", "cli", true), info("file", "cli", false), info("todo", "cli", false), info("browser", "cli", true))
        );
        assertThat(plan.enabled()).contains("web", "file", "todo");
        assertThat(plan.enabled()).doesNotContain("browser");
        assertThat(plan.known()).doesNotContain("browser");
        assertThat(plan.disabled()).doesNotContain("browser");
    }

    @Test
    void isAtomicToolset_shouldSkipComposites() {
        assertThat(HermesToolsetService.isAtomicToolset("web")).isTrue();
        assertThat(HermesToolsetService.isAtomicToolset("plan")).isTrue();
        assertThat(HermesToolsetService.isAtomicToolset("hermes-api-server")).isFalse();
        assertThat(HermesToolsetService.isAtomicToolset("stt")).isFalse();
        assertThat(HermesToolsetService.isAtomicToolset("no_mcp")).isFalse();
        assertThat(HermesToolsetService.isAtomicToolset("browser")).isFalse();
        assertThat(HermesToolsetService.isAtomicToolset("memory")).isFalse();
        assertThat(HermesToolsetService.isAtomicToolset("discord")).isFalse();
    }

    @Test
    void toggle_shouldRejectUnknownToolset() {
        HermesAgentClient hermes = mock(HermesAgentClient.class);
        HermesAgentClient.ToolsetWriteResult r = service(hermes).toggle("worker", "discord", true);
        assertThat(r.ok()).isFalse();
        assertThat(r.message()).contains("未知");
        verify(hermes, org.mockito.Mockito.never()).toggleToolset(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void extrasBeyond_shouldIgnoreNoMcpAndKeepUnexpected() {
        assertThat(HermesToolsetService.extrasBeyond(
                List.of("web", "file", "browser", "no_mcp"),
                List.of("web", "file")
        )).containsExactly("browser");
    }

    @Test
    void ensureChatGatewaySynced_shouldNotWriteApiServer() {
        HermesAgentClient hermes = mock(HermesAgentClient.class);
        when(hermes.isConfigured()).thenReturn(true);

        service(hermes).ensureChatGatewaySynced("default");

        verify(hermes, org.mockito.Mockito.never()).syncChatGatewayToolsets(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(hermes, org.mockito.Mockito.never()).listToolsets(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureChatGatewaySynced_longHorizonShouldEnableCliTaskToolsets() {
        HermesAgentClient hermes = mock(HermesAgentClient.class);
        when(hermes.isConfigured()).thenReturn(true);
        when(hermes.listToolsets(eq(UserContext.DEFAULT_USER_ID), eq("default"))).thenReturn(List.of(
                info("web", "cli", true),
                info("todo", "cli", false),
                info("kanban", "cli", false)
        ));
        when(hermes.toggleToolset(eq(UserContext.DEFAULT_USER_ID), eq("default"), eq("todo"), eq(true)))
                .thenReturn(new HermesAgentClient.ToolsetWriteResult(true, "todo", true, ""));
        when(hermes.toggleToolset(eq(UserContext.DEFAULT_USER_ID), eq("default"), eq("kanban"), eq(true)))
                .thenReturn(new HermesAgentClient.ToolsetWriteResult(true, "kanban", true, ""));

        service(hermes).ensureChatGatewaySynced("default", true);

        verify(hermes).toggleToolset(eq(UserContext.DEFAULT_USER_ID), eq("default"), eq("todo"), eq(true));
        verify(hermes).toggleToolset(eq(UserContext.DEFAULT_USER_ID), eq("default"), eq("kanban"), eq(true));
        verify(hermes, org.mockito.Mockito.never()).syncChatGatewayToolsets(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static List<String> defaultPlusNoMcp() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(ClaudeCodeToolsets.DEFAULT_ENABLED);
        out.add("no_mcp");
        return out;
    }

    private static HermesAgentClient.ToolsetInfo info(String name, String platform, boolean enabled) {
        return new HermesAgentClient.ToolsetInfo(
                name, name, "", platform, platform, enabled, true, List.of(name + "_tool"));
    }
}
