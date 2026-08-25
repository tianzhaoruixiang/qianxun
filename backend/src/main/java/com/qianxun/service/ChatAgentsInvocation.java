package com.qianxun.service;

/**
 * Hermes 原生 {@code /agents}（别名 {@code /tasks}、{@code /task}）：查看当前会话活跃子智能体与运行中任务。
 * <p>
 * 与 {@link ChatSkillInvocation} / {@link ChatGoalInvocation} 同通道——Dashboard 走
 * {@code slash.exec}；本命令为只读状态输出，不继续 {@code prompt.submit}。
 */
public final class ChatAgentsInvocation {

    public static final String HERMES_COMMAND = "/agents";
    public static final String HERMES_ALIAS = "/tasks";
    /** 千寻前端常用短别名，后端归一为 {@link #HERMES_COMMAND}。 */
    public static final String HERMES_ALIAS_SHORT = "/task";

    /** 千寻聊天气泡展示用（不发给 Hermes 斜杠）。 */
    public static final String LOCAL_DISPLAY = "【子智能体】查看运行中的任务";

    private ChatAgentsInvocation() {}

    public static String formatHermesCommand() {
        return HERMES_COMMAND;
    }

    public static boolean looksLikeCommand(String content) {
        String t = content == null ? "" : content.trim();
        if (t.isEmpty()) {
            return false;
        }
        String lower = t.toLowerCase();
        return lower.equals("/agents")
                || lower.equals("/tasks")
                || lower.equals("/task")
                || lower.equals(LOCAL_DISPLAY)
                || lower.startsWith("/agents ")
                || lower.startsWith("/tasks ")
                || lower.startsWith("/task ");
    }

    public static String unavailableMessage() {
        return "当前未启用智能体运行器，无法查询子智能体与运行中任务。";
    }

    /**
     * 从用户输入取出 {@code /task} 后的查询段（如 {@code log deleg_xxx 0}）。
     * 纯状态查询返回空串。
     */
    public static String extractStatusQuery(String content) {
        String t = content == null ? "" : content.trim();
        if (t.isEmpty() || t.equals(LOCAL_DISPLAY)) {
            return "";
        }
        String lower = t.toLowerCase();
        if (lower.startsWith("/agents")) {
            t = t.substring("/agents".length()).trim();
        } else if (lower.startsWith("/tasks")) {
            t = t.substring("/tasks".length()).trim();
        } else if (lower.startsWith("/task")) {
            t = t.substring("/task".length()).trim();
        } else if (t.startsWith(LOCAL_DISPLAY)) {
            t = t.substring(LOCAL_DISPLAY.length()).trim();
            if (t.startsWith("·") || t.startsWith("（") || t.startsWith("(")) {
                t = t.substring(1).trim();
            }
            if (t.endsWith("）") || t.endsWith(")")) {
                t = t.substring(0, t.length() - 1).trim();
            }
        }
        if (t.isEmpty()) {
            return "";
        }
        String l = t.toLowerCase();
        if (l.equals("status") || l.equals("状态") || l.equals("任务") || l.equals("list") || l.equals("ls")) {
            return "";
        }
        return t;
    }

    public static String displayContent(String statusQuery) {
        String q = statusQuery == null ? "" : statusQuery.trim();
        if (q.isBlank()) {
            return LOCAL_DISPLAY;
        }
        return LOCAL_DISPLAY + "（" + q + "）";
    }
}
