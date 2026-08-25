package com.qianxun.llm;

import com.qianxun.domain.ToolDisplayName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Claude Code / Agent SDK 内置工具英文名 → 中文展示名 / 图标。
 * <p>
 * 名称对齐官方 Tools Reference（PascalCase，如 {@code Bash}、{@code WebSearch}）。
 * 流式事件里的 {@code tool_use.name} 即本表键。
 */
public final class ClaudeCodeToolCatalog {

    public record Entry(String code, String displayName, String iconKind, int sortOrder) {}

    /** 精确匹配（保留官方大小写） */
    private static final Map<String, Entry> BY_CODE = new LinkedHashMap<>();
    /** 小写索引，便于忽略大小写查找 */
    private static final Map<String, Entry> BY_LOWER = new LinkedHashMap<>();

    static {
        int n = 0;
        // ── 文件 ──
        add("Read", "读取文件", "extract", n += 10);
        add("Write", "写入文件", "file", n += 10);
        add("Edit", "编辑文件", "code", n += 10);
        add("MultiEdit", "批量编辑", "code", n += 10);
        add("NotebookEdit", "编辑 Notebook", "code", n += 10);
        add("Glob", "按名查找文件", "search", n += 10);
        add("Grep", "内容搜索", "search", n += 10);
        add("LSP", "代码智能", "code", n += 10);

        // ── 终端 ──
        add("Bash", "命令行", "terminal", n += 10);
        add("BashOutput", "读取命令输出", "terminal", n += 10);
        add("PowerShell", "PowerShell", "terminal", n += 10);
        add("Monitor", "后台监视命令", "terminal", n += 10);

        // ── 网络 ──
        add("WebSearch", "网页搜索", "search", n += 10);
        add("WebFetch", "抓取网页", "extract", n += 10);

        // ── 委派 / 智能体 ──
        add("Agent", "子智能体", "agent", n += 10);
        add("Task", "子任务（旧）", "agent", n += 10);
        add("SendMessage", "发送消息给智能体", "agent", n += 10);
        add("ListAgents", "列出智能体", "agent", n += 10);
        add("TeamCreate", "创建智能体团队", "agent", n += 10);
        add("TeamDelete", "删除智能体团队", "agent", n += 10);

        // ── 任务清单 ──
        add("TodoWrite", "任务清单", "todo", n += 10);
        add("TaskCreate", "创建任务", "todo", n += 10);
        add("TaskGet", "查看任务", "todo", n += 10);
        add("TaskList", "任务列表", "todo", n += 10);
        add("TaskUpdate", "更新任务", "todo", n += 10);
        add("TaskStop", "停止后台任务", "todo", n += 10);
        add("TaskOutput", "读取任务输出", "todo", n += 10);

        // ── 技能 / 交互 ──
        add("Skill", "执行技能", "skill", n += 10);
        add("AskUserQuestion", "向用户提问", "ask", n += 10);
        add("ToolSearch", "搜索工具", "search", n += 10);

        // ── 计划 / 工作区 ──
        add("EnterPlanMode", "进入计划模式", "ask", n += 10);
        add("ExitPlanMode", "退出计划模式", "ask", n += 10);
        add("EnterWorktree", "进入 Worktree", "file", n += 10);
        add("ExitWorktree", "退出 Worktree", "file", n += 10);

        // ── 定时 / 循环 ──
        add("CronCreate", "创建定时任务", "clock", n += 10);
        add("CronDelete", "删除定时任务", "clock", n += 10);
        add("CronList", "定时任务列表", "clock", n += 10);
        add("ScheduleWakeup", "调度唤醒", "clock", n += 10);

        // ── MCP ──
        add("ListMcpResourcesTool", "列出 MCP 资源", "gear", n += 10);
        add("ReadMcpResourceTool", "读取 MCP 资源", "extract", n += 10);
        add("WaitForMcpServers", "等待 MCP 连接", "gear", n += 10);

        // ── 其它平台能力（出现时也能展示） ──
        add("Artifact", "发布 Artifact", "file", n += 10);
        add("Workflow", "运行工作流", "agent", n += 10);
        add("RemoteTrigger", "远程例程", "clock", n += 10);
        add("PushNotification", "推送通知", "speak", n += 10);
        add("SendUserFile", "发送文件给用户", "file", n += 10);
        add("ShareOnboardingGuide", "分享入门指南", "social", n += 10);
        add("ReportFindings", "报告审查发现", "code", n += 10);
        add("EndConversation", "结束会话", "ask", n += 10);
    }

    private ClaudeCodeToolCatalog() {}

    private static void add(String code, String displayName, String iconKind, int sortOrder) {
        Entry e = new Entry(code, displayName, iconKind, sortOrder);
        BY_CODE.put(code, e);
        BY_LOWER.put(code.toLowerCase(Locale.ROOT), e);
    }

    public static List<ToolDisplayName> seedRows() {
        List<ToolDisplayName> out = new ArrayList<>(BY_CODE.size());
        for (Entry e : BY_CODE.values()) {
            out.add(new ToolDisplayName(e.code(), e.displayName(), e.sortOrder()));
        }
        return out;
    }

    public static Entry find(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return null;
        }
        String raw = toolCode.trim();
        Entry e = BY_CODE.get(raw);
        if (e != null) {
            return e;
        }
        return BY_LOWER.get(raw.toLowerCase(Locale.ROOT));
    }

    public static String displayName(String toolCode) {
        Entry e = find(toolCode);
        return e == null ? null : e.displayName();
    }

    public static String iconKind(String toolCode) {
        Entry e = find(toolCode);
        return e == null ? null : e.iconKind();
    }

    public static List<String> allCodes() {
        return List.copyOf(BY_CODE.keySet());
    }

    /** 未收录的名称（如 MCP 工具）给出可读回退，不再走 Hermes 目录。 */
    public static String fallbackDisplayName(String toolCode) {
        String mapped = displayName(toolCode);
        if (mapped != null) {
            return mapped;
        }
        if (toolCode == null || toolCode.isBlank()) {
            return "工具";
        }
        String code = toolCode.trim();
        if (code.startsWith("mcp__")) {
            String rest = code.substring("mcp__".length()).replace("__", " · ");
            return "MCP · " + rest;
        }
        return "工具";
    }

    public static String fallbackIconKind(String toolCode) {
        String kind = iconKind(toolCode);
        if (kind != null) {
            return kind;
        }
        return "gear";
    }
}
