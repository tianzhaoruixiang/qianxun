package com.qianxun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.web.dto.SessionGoalRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 长程目标：千寻侧持久化会话目标。聊天走 Dashboard 时由原生 {@code slash.exec /goal}
 * 生效，不再把 last user 改写成斜杠命令去打 OpenAI 兼容网关。
 * <p>
 * 若回退到 {@code POST /v1/chat/completions}，仍可用 {@link #apply} / {@link #applyClear}
 * 把最后一条 user 改写成 {@code /goal ...}（该网关不跑 slash dispatcher）。
 */
public final class ChatGoalInvocation {

    static final int MAX_TITLE = 120;
    static final int MAX_DESCRIPTION = 2_000;
    static final int MAX_STEPS = 1_500;
    static final int MAX_CONSTRAINTS = 800;
    static final int MAX_JSON_CHARS = 8_000;

    public static final String HERMES_CLEAR_COMMAND = "/goal clear";
    static final String LOCAL_DISPLAY_PREFIX = "【长程目标】";

    /** Hermes {@code /goal} 子命令，不能当作目标标题单独下发。 */
    private static final Set<String> HERMES_GOAL_VERBS = Set.of(
            "clear", "stop", "done", "status", "show", "pause", "resume",
            "draft", "wait", "unwait", "gate"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Goal(String title, String description, String steps, String constraints) {
        public boolean isBlank() {
            return isBlank(title) && isBlank(description) && isBlank(steps) && isBlank(constraints);
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
                trim(request.constraints())
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
            return clip(new Goal(
                    textOf(n, "title"),
                    textOf(n, "description"),
                    textOf(n, "steps"),
                    textOf(n, "constraints")
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
            return MAPPER.writeValueAsString(Map.of(
                    "title", g.title(),
                    "description", g.description(),
                    "steps", g.steps(),
                    "constraints", g.constraints()
            ));
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
                clip(goal.constraints(), MAX_CONSTRAINTS)
        );
    }

    /** 千寻聊天气泡展示用（不发给 Hermes）。 */
    public static String formatUserVisible(Goal goal) {
        Goal g = clip(goal);
        StringBuilder sb = new StringBuilder();
        sb.append(LOCAL_DISPLAY_PREFIX);
        if (!g.title().isBlank()) {
            sb.append(g.title());
        } else {
            sb.append("未命名目标");
        }
        if (!g.description().isBlank()) {
            sb.append("\n成功标准：").append(g.description());
        }
        if (!g.steps().isBlank()) {
            sb.append("\n步骤：").append(g.steps());
        }
        if (!g.constraints().isBlank()) {
            sb.append("\n约束：").append(g.constraints());
        }
        sb.append("\n请按该目标分步执行，在达成成功标准前不要停止。");
        return sb.toString();
    }

    /**
     * Hermes 原生 {@code /goal} 正文（含可选 completion contract 字段）。
     * 字段前缀对齐官方 {@code parse_contract}：{@code verify:} / {@code constraints:}。
     */
    public static String formatHermesCommand(Goal goal) {
        Goal g = clip(goal);
        String headline = hermesHeadline(g);
        StringBuilder sb = new StringBuilder();
        sb.append("/goal ").append(headline);
        if (!g.steps().isBlank()) {
            sb.append('\n').append(g.steps().trim());
        }
        if (!g.description().isBlank()) {
            sb.append("\nverify: ").append(g.description().trim());
        }
        if (!g.constraints().isBlank()) {
            sb.append("\nconstraints: ").append(g.constraints().trim());
        }
        return sb.toString();
    }

    public static boolean looksLikeLocalGoalDisplay(String content) {
        String t = trim(content);
        return t.startsWith(LOCAL_DISPLAY_PREFIX);
    }

    /**
     * 仍可用于调试文案；不再注入 Hermes 系统提示（会改变 api-session 指纹，且不是原生 /goal）。
     */
    public static String systemHint(Goal goal, boolean kickoff) {
        Goal g = clip(goal);
        if (g.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (kickoff) {
            sb.append("你正在执行一项长程任务。明确目标与成功标准后，请分步实际执行，");
            sb.append("在目标达成前不要停止，不要只做开场白或只列出计划就结束。");
            sb.append("可用任务清单记录步骤并勾选完成项；需要落盘时请真正写入文件。");
        } else {
            sb.append("当前会话仍有未完成的长程目标，请继续推进直至满足成功标准，完成前不要停。");
        }
        if (!g.title().isBlank()) {
            sb.append("\n目标：").append(g.title());
        }
        if (!g.description().isBlank()) {
            sb.append("\n成功标准：").append(g.description());
        }
        if (kickoff && !g.steps().isBlank()) {
            sb.append("\n建议步骤：").append(g.steps());
        }
        if (kickoff && !g.constraints().isBlank()) {
            sb.append("\n约束：").append(g.constraints());
        }
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

    private static String hermesHeadline(Goal g) {
        String title = g.title().isBlank() ? "未命名目标" : g.title().trim();
        String first = title.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (HERMES_GOAL_VERBS.contains(first)) {
            return "长程任务 " + title;
        }
        return title;
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

    private static final Goal EMPTY = new Goal("", "", "", "");
}
