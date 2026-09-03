package com.qianxun.llm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 千寻工具市场的原子工具集名 → Claude Code {@code allowedTools}。
 * {@code displayTools} 使用 Claude 官方 PascalCase 名，便于前端中文映射。
 */
public final class ClaudeCodeToolsets {

    public record Def(
            String name,
            String label,
            String description,
            List<String> claudeTools,
            List<String> displayTools
    ) {}

    public static final List<Def> CATALOG = List.of(
            new Def("web", "Web", "网页搜索与抓取",
                    List.of("WebSearch", "WebFetch"),
                    List.of("WebSearch", "WebFetch")),
            new Def("file", "File", "读写与搜索工作区文件",
                    List.of("Read", "Write", "Edit", "MultiEdit", "Glob", "Grep", "NotebookEdit", "LSP"),
                    List.of("Read", "Write", "Edit", "MultiEdit", "Glob", "Grep", "NotebookEdit", "LSP")),
            new Def("terminal", "Terminal", "命令行",
                    List.of("Bash", "BashOutput", "PowerShell", "Monitor"),
                    List.of("Bash", "BashOutput", "PowerShell", "Monitor")),
            new Def("code_execution", "Code", "在终端中执行代码",
                    List.of("Bash"),
                    List.of("Bash")),
            new Def("delegation", "Delegation", "子智能体委派",
                    List.of("Agent", "Task", "SendMessage", "ListAgents", "TeamCreate", "TeamDelete", "TaskStop", "TaskOutput"),
                    List.of("Agent", "Task", "SendMessage", "ListAgents", "TeamCreate", "TeamDelete", "TaskStop", "TaskOutput")),
            new Def("skills", "Skills", "技能",
                    List.of("Skill"),
                    List.of("Skill")),
            new Def("todo", "Todo", "任务清单",
                    List.of("TodoWrite", "TaskCreate", "TaskGet", "TaskList", "TaskUpdate"),
                    List.of("TodoWrite", "TaskCreate", "TaskGet", "TaskList", "TaskUpdate")),
            new Def("kanban", "Kanban", "看板",
                    List.of("TodoWrite", "TaskCreate", "TaskList", "TaskUpdate"),
                    List.of("TodoWrite", "TaskCreate", "TaskList", "TaskUpdate")),
            new Def("plan", "Plan", "计划模式与向用户提问",
                    List.of("AskUserQuestion", "ToolSearch", "EnterPlanMode", "ExitPlanMode"),
                    List.of("AskUserQuestion", "ToolSearch", "EnterPlanMode", "ExitPlanMode"))
    );

    /** 缺省打开目录内全部 Claude Code 工具集。 */
    public static final List<String> DEFAULT_ENABLED = CATALOG.stream().map(Def::name).toList();

    public static boolean isDefaultEnabled(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String want = name.trim();
        for (String n : DEFAULT_ENABLED) {
            if (n.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    private ClaudeCodeToolsets() {}

    public static Def find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String want = name.trim();
        for (Def d : CATALOG) {
            if (d.name().equalsIgnoreCase(want)) {
                return d;
            }
        }
        return null;
    }

    public static boolean isKnown(String name) {
        return find(name) != null;
    }

    public static String allowedToolsCsv(List<String> enabledToolsets) {
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        Set<String> on = lowerSet(enabledToolsets);
        if (on.isEmpty()) {
            on = lowerSet(DEFAULT_ENABLED);
        }
        for (Def d : CATALOG) {
            if (!on.contains(d.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            tools.addAll(d.claudeTools());
        }
        if (tools.isEmpty()) {
            tools.add("Read");
        }
        return String.join(",", tools);
    }

    public static List<HermesAgentClient.ToolsetInfo> toInfos(Set<String> enabled) {
        Set<String> on = enabled == null ? Set.of() : enabled;
        List<HermesAgentClient.ToolsetInfo> out = new ArrayList<>();
        for (Def d : CATALOG) {
            boolean isOn = containsIgnoreCase(on, d.name());
            out.add(new HermesAgentClient.ToolsetInfo(
                    d.name(), d.label(), d.description(),
                    "cli", "CLI", isOn, true, d.displayTools()));
        }
        return List.copyOf(out);
    }

    static boolean containsIgnoreCase(Set<String> names, String want) {
        if (names == null || want == null) {
            return false;
        }
        for (String n : names) {
            if (want.equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> lowerSet(List<String> names) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (names == null) {
            return out;
        }
        for (String n : names) {
            if (n != null && !n.isBlank() && !"no_mcp".equalsIgnoreCase(n.trim())) {
                out.add(n.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
