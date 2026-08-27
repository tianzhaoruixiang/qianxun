package com.qianxun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.web.dto.SessionGoalRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 长程目标：千寻侧持久化会话目标，聊天走 Claude Code 原生 {@code /goal &lt;condition&gt;}。
 * <p>
 * 官方入参只有一条完成条件（最长 4000 字），可选在条件末尾写
 * {@code or stop after N turns}。没有独立的 title / verify / steps schema。
 * 表单字段会在 {@link #formatHermesCommand} 里拼成该条件。
 * <p>
 * 若回退到 {@code POST /v1/chat/completions}，仍可用 {@link #apply} / {@link #applyClear}
 * 把最后一条 user 改写成 {@code /goal ...}。
 */
public final class ChatGoalInvocation {

    /** 与 Claude Code 文档一致：条件最长 4000 字符。 */
    static final int MAX_CONDITION = 4_000;
    static final int MAX_TITLE = 120;
    static final int MAX_DESCRIPTION = MAX_CONDITION;
    static final int MAX_STEPS = 800;
    static final int MAX_CONSTRAINTS = 800;
    static final int MAX_JSON_CHARS = 8_000;
    static final int MAX_STOP_TURNS = 200;

    public static final String HERMES_CLEAR_COMMAND = "/goal clear";
    static final String LOCAL_DISPLAY_PREFIX = "【长程目标】";

    /**
     * Claude Code {@code /goal} 无参看状态；{@code clear} 及别名会清除目标，
     * 不能当作完成条件的第一个词单独下发。
     */
    private static final Set<String> GOAL_CONTROL_WORDS = Set.of(
            "clear", "stop", "off", "reset", "none", "cancel"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Goal(
            String title,
            String description,
            String steps,
            String constraints,
            Integer stopAfterTurns
    ) {
        public Goal(String title, String description, String steps, String constraints) {
            this(title, description, steps, constraints, null);
        }

        public boolean isBlank() {
            return isBlank(title) && isBlank(description) && isBlank(steps) && isBlank(constraints)
                    && (stopAfterTurns == null || stopAfterTurns <= 0);
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }

    private ChatGoalInvocation() {}

    public static Goal fromRequest(SessionGoalRequest request) {
        if (request == null) {
            return EMPTY;
        }
        return clip(new Goal(
                trim(request.title()),
                trim(request.description()),
                trim(request.steps()),
                trim(request.constraints()),
                request.stopAfterTurns()
        ));
    }

    public static Goal parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        String text = raw.trim();
        if (text.length() > MAX_JSON_CHARS) {
            text = text.substring(0, MAX_JSON_CHARS);
        }
        try {
            JsonNode n = MAPPER.readTree(text);
            if (n == null || !n.isObject()) {
                return EMPTY;
            }
            Integer turns = null;
            if (n.path("stopAfterTurns").isNumber()) {
                turns = n.path("stopAfterTurns").asInt();
            }
            return clip(new Goal(
                    textOf(n, "title"),
                    textOf(n, "description"),
                    textOf(n, "steps"),
                    textOf(n, "constraints"),
                    turns
            ));
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    public static String toJson(Goal goal) {
        Goal g = clip(goal);
        if (g.isBlank()) {
            return "";
        }
        try {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("title", g.title());
            map.put("description", g.description());
            map.put("steps", g.steps());
            map.put("constraints", g.constraints());
            if (g.stopAfterTurns() != null && g.stopAfterTurns() > 0) {
                map.put("stopAfterTurns", g.stopAfterTurns());
            }
            return MAPPER.writeValueAsString(map);
        } catch (Exception ex) {
            return "";
        }
    }

    public static Goal clip(Goal goal) {
        if (goal == null) {
            return EMPTY;
        }
        return new Goal(
                clip(goal.title(), MAX_TITLE),
                clip(goal.description(), MAX_DESCRIPTION),
                clip(goal.steps(), MAX_STEPS),
                clip(goal.constraints(), MAX_CONSTRAINTS),
                clipTurns(goal.stopAfterTurns())
        );
    }

    /** 千寻聊天气泡展示用（不发给 Claude Code）。 */
    public static String formatUserVisible(Goal goal) {
        Goal g = clip(goal);
        StringBuilder sb = new StringBuilder();
        sb.append(LOCAL_DISPLAY_PREFIX);
        String heading = g.title().isBlank() ? firstLine(formatCondition(g), 40) : g.title();
        sb.append(heading.isBlank() ? "未命名目标" : heading);
        if (!g.description().isBlank()) {
            sb.append("\n完成条件：").append(g.description());
        }
        if (!g.steps().isBlank()) {
            sb.append("\n验收方式：").append(g.steps());
        }
        if (!g.constraints().isBlank()) {
            sb.append("\n约束：").append(g.constraints());
        }
        if (g.stopAfterTurns() != null && g.stopAfterTurns() > 0) {
            sb.append("\n轮次上限：").append(g.stopAfterTurns());
        }
        return sb.toString();
    }

    /**
     * Claude Code 原生命令：{@code /goal &lt;condition&gt;}。
     * 评估器只看对话里已出现的证据，条件须可验证。
     */
    public static String formatHermesCommand(Goal goal) {
        Goal g = clip(goal);
        String condition = formatCondition(g);
        if (startsWithControlWord(condition)) {
            condition = "完成以下目标：" + condition;
        }
        condition = clip(condition, MAX_CONDITION);
        return "/goal " + condition;
    }

    /** 拼成官方完成条件（不含 {@code /goal} 前缀）。 */
    public static String formatCondition(Goal goal) {
        Goal g = clip(goal);
        String title = g.title().trim();
        String description = g.description().trim();
        String core;
        if (!description.isBlank() && !title.isBlank() && !description.contains(title)) {
            core = title + "：" + description;
        } else if (!description.isBlank()) {
            core = description;
        } else if (!title.isBlank()) {
            core = title;
        } else {
            core = "未命名目标";
        }
        StringBuilder sb = new StringBuilder(core.replace('\n', ' ').trim());
        if (!g.steps().isBlank()) {
            sb.append("；验收方式：").append(g.steps().replace('\n', ' ').trim());
        }
        if (!g.constraints().isBlank()) {
            sb.append("；约束：").append(g.constraints().replace('\n', ' ').trim());
        }
        if (g.stopAfterTurns() != null && g.stopAfterTurns() > 0) {
            sb.append(" or stop after ").append(g.stopAfterTurns()).append(" turns");
        }
        return clip(sb.toString().trim(), MAX_CONDITION);
    }

    public static boolean looksLikeLocalGoalDisplay(String content) {
        String t = trim(content);
        return t.startsWith(LOCAL_DISPLAY_PREFIX);
    }

    /**
     * 仍可用于调试文案；不再注入系统提示（会改变会话指纹，且不是原生 /goal）。
     */
    public static String systemHint(Goal goal, boolean kickoff) {
        Goal g = clip(goal);
        if (g.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (kickoff) {
            sb.append("你正在执行一项长程任务。按完成条件持续推进，达成前不要把控制权交回。");
        } else {
            sb.append("当前会话仍有未完成的长程目标，请继续推进直至满足完成条件。");
        }
        sb.append("\n完成条件：").append(formatCondition(g));
        return sb.toString();
    }

    public static List<Map<String, String>> apply(
            List<Map<String, String>> messages,
            Goal goal,
            boolean kickoff
    ) {
        List<Map<String, String>> src = messages == null ? List.of() : messages;
        Goal g = clip(goal);
        if (g.isBlank()) {
            return src;
        }
        String command = formatHermesCommand(g);
        List<Map<String, String>> out = new ArrayList<>(src.size());
        int lastUser = lastUserIndex(src);
        for (int i = 0; i < src.size(); i++) {
            Map<String, String> m = src.get(i);
            if (m == null) {
                continue;
            }
            if (!"user".equals(m.get("role"))) {
                out.add(m);
                continue;
            }
            String content = m.get("content");
            if (kickoff && i == lastUser) {
                out.add(Map.of("role", "user", "content", command));
            } else if (looksLikeLocalGoalDisplay(content)) {
                out.add(Map.of("role", "user", "content", command));
            } else {
                out.add(m);
            }
        }
        if (kickoff && lastUser < 0) {
            out.add(Map.of("role", "user", "content", command));
        }
        return out;
    }

    public static List<Map<String, String>> applyClear(List<Map<String, String>> messages) {
        List<Map<String, String>> src = messages == null ? List.of() : messages;
        List<Map<String, String>> out = new ArrayList<>(src.size());
        int lastUser = lastUserIndex(src);
        for (int i = 0; i < src.size(); i++) {
            Map<String, String> m = src.get(i);
            if (m != null && i == lastUser) {
                out.add(Map.of("role", "user", "content", HERMES_CLEAR_COMMAND));
            } else if (m != null) {
                out.add(m);
            }
        }
        if (lastUser < 0) {
            out.add(Map.of("role", "user", "content", HERMES_CLEAR_COMMAND));
        }
        return out;
    }

    private static boolean startsWithControlWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        for (String w : GOAL_CONTROL_WORDS) {
            if (t.equals(w) || t.startsWith(w + " ") || t.startsWith(w + "：") || t.startsWith(w + ":")) {
                return true;
            }
        }
        return false;
    }

    private static String firstLine(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String t = text.trim();
        int nl = t.indexOf('\n');
        if (nl >= 0) {
            t = t.substring(0, nl).trim();
        }
        return clip(t, max);
    }

    private static Integer clipTurns(Integer n) {
        if (n == null || n <= 0) {
            return null;
        }
        return Math.min(n, MAX_STOP_TURNS);
    }

    private static int lastUserIndex(List<Map<String, String>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> m = messages.get(i);
            if (m != null && "user".equals(m.get("role"))) {
                return i;
            }
        }
        return -1;
    }

    private static String textOf(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        return v.asText("");
    }

    private static String clip(String s, int max) {
        String t = trim(s);
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static final Goal EMPTY = new Goal("", "", "", "", null);
}
