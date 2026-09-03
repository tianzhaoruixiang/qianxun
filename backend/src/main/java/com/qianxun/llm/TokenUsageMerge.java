package com.qianxun.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 透传上游 usage，不自行估算 token。
 * <ul>
 *   <li>OpenAI：同轮多次请求做加法；占用取最近一次 prompt。</li>
 *   <li>Dashboard 会话快照（{@code sessionSnapshot}）：input/output/total 已是会话累计，
 *       只保留最新一帧；占用优先用 {@code context_used}/{@code context_percent}。</li>
 *   <li>Claude Code {@code context_usage}（{@code contextSnapshot}）：只刷新上下文窗口与 cache，
 *       不改变本轮计费累加。</li>
 * </ul>
 */
public final class TokenUsageMerge {

    private TokenUsageMerge() {
    }

    public static Map<String, Object> accumulate(
            Map<String, Object> previous,
            OpenAiCompatibleStreamClient.TokenUsage usage,
            int fallbackContextWindow
    ) {
        if (usage == null) {
            return previous;
        }
        if (usage.liveOccupancy()) {
            return mergeLiveOccupancy(previous, usage, fallbackContextWindow);
        }
        if (usage.contextSnapshot()) {
            return mergeContextSnapshot(previous, usage, fallbackContextWindow);
        }
        if (usage.sessionSnapshot() || boolVal(previous, "sessionSnapshot")) {
            return mergeSnapshot(previous, usage, fallbackContextWindow);
        }
        return mergeDelta(previous, usage, fallbackContextWindow);
    }

    private static Map<String, Object> mergeSnapshot(
            Map<String, Object> previous,
            OpenAiCompatibleStreamClient.TokenUsage usage,
            int fallbackContextWindow
    ) {
        int prompt = pickSnapshot(usage.promptTokens(), intVal(previous, "promptTokens"));
        int completion = pickSnapshot(usage.completionTokens(), intVal(previous, "completionTokens"));
        int total = usage.totalTokens() != null
                ? Math.max(0, usage.totalTokens())
                : (prompt > 0 || completion > 0 ? prompt + completion : intVal(previous, "totalTokens"));
        int window = pickWindow(usage.contextWindow(), previous, fallbackContextWindow);
        int contextUsed = pickSnapshot(usage.contextUsed(), intVal(previous, "contextUsed"));
        double percent = usage.contextPercent() != null
                ? clampPercent(usage.contextPercent())
                : (window > 0 && contextUsed > 0
                        ? clampPercent(contextUsed * 100.0 / window)
                        : doubleVal(previous, "contextPercent"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("promptTokens", prompt);
        data.put("completionTokens", completion);
        data.put("totalTokens", total);
        data.put("contextWindow", window);
        data.put("contextUsed", contextUsed);
        data.put("contextPercent", percent);
        data.put("sessionSnapshot", true);
        applyClaudeExtras(previous, data, usage);
        copyGenerationMs(previous, data);
        return data;
    }

    /** Claude SDK getContextUsage：只刷新上下文占用，计费字段沿用 previous。 */
    private static Map<String, Object> mergeContextSnapshot(
            Map<String, Object> previous,
            OpenAiCompatibleStreamClient.TokenUsage usage,
            int fallbackContextWindow
    ) {
        Map<String, Object> data = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
        int window = pickWindow(usage.contextWindow(), previous, fallbackContextWindow);
        int contextUsed = pickSnapshot(usage.contextUsed(), intVal(previous, "contextUsed"));
        double percent = usage.contextPercent() != null
                ? clampPercent(usage.contextPercent())
                : (window > 0 && contextUsed > 0
                        ? clampPercent(contextUsed * 100.0 / window)
                        : doubleVal(previous, "contextPercent"));
        data.put("contextWindow", window);
        data.put("contextUsed", contextUsed);
        data.put("contextPercent", percent);
        data.put("contextSnapshot", true);
        data.remove("live");
        data.remove("liveOutputTokens");
        applyClaudeExtras(previous, data, usage);
        return data;
    }

    /** 流式中间帧：标记 live，不把 API input_tokens 当成上下文窗口占用。 */
    private static Map<String, Object> mergeLiveOccupancy(
            Map<String, Object> previous,
            OpenAiCompatibleStreamClient.TokenUsage usage,
            int fallbackContextWindow
    ) {
        int window = pickWindow(usage.contextWindow(), previous, fallbackContextWindow);
        Map<String, Object> data = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
        data.put("contextWindow", window);
        data.put("live", true);
        return data;
    }

    private static Map<String, Object> mergeDelta(
            Map<String, Object> previous,
            OpenAiCompatibleStreamClient.TokenUsage usage,
            int fallbackContextWindow
    ) {
        int prompt = nz(usage.promptTokens());
        int completion = nz(usage.completionTokens());
        int total = usage.totalTokens() != null
                ? Math.max(0, usage.totalTokens())
                : prompt + completion;
        int window = pickWindow(usage.contextWindow(), previous, fallbackContextWindow);

        int sumPrompt = intVal(previous, "promptTokens") + prompt;
        int sumCompletion = intVal(previous, "completionTokens") + completion;
        int sumTotal = intVal(previous, "totalTokens") + total;
        // contextUsed 仅由 context_usage（contextSnapshot）写入；result/live 的 input_tokens 不是窗口占用
        int contextUsed = intVal(previous, "contextUsed");
        double percent = contextUsed > 0 && window > 0
                ? clampPercent(contextUsed * 100.0 / window)
                : doubleVal(previous, "contextPercent");

        Map<String, Object> data = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
        data.put("promptTokens", sumPrompt);
        data.put("completionTokens", sumCompletion);
        data.put("totalTokens", sumTotal);
        data.put("contextWindow", window);
        if (contextUsed > 0) {
            data.put("contextUsed", contextUsed);
            data.put("contextPercent", percent);
        } else {
            data.remove("contextUsed");
            data.remove("contextPercent");
        }
        applyClaudeExtras(previous, data, usage);
        copyGenerationMs(previous, data);
        return data;
    }

    private static void applyClaudeExtras(
            Map<String, Object> previous,
            Map<String, Object> data,
            OpenAiCompatibleStreamClient.TokenUsage usage
    ) {
        if (usage.treePromptTokens() != null) {
            data.put("treePromptTokens", usage.treePromptTokens());
        } else {
            copyField(previous, data, "treePromptTokens");
        }
        if (usage.treeCompletionTokens() != null) {
            data.put("treeCompletionTokens", usage.treeCompletionTokens());
        } else {
            copyField(previous, data, "treeCompletionTokens");
        }
        if (usage.totalCostUsd() != null && usage.totalCostUsd() >= 0) {
            data.put("totalCostUsd", usage.totalCostUsd());
        } else {
            copyField(previous, data, "totalCostUsd");
        }
        if (usage.cacheReadTokens() != null) {
            data.put("cacheReadTokens", usage.cacheReadTokens());
        } else {
            copyField(previous, data, "cacheReadTokens");
        }
        if (usage.cacheCreationTokens() != null) {
            data.put("cacheCreationTokens", usage.cacheCreationTokens());
        } else {
            copyField(previous, data, "cacheCreationTokens");
        }
        if (usage.durationMs() != null && usage.durationMs() >= 0) {
            data.put("generationMs", usage.durationMs());
        }
    }

    private static void copyField(Map<String, Object> previous, Map<String, Object> data, String key) {
        if (previous == null || data.containsKey(key)) {
            return;
        }
        Object v = previous.get(key);
        if (v != null) {
            data.put(key, v);
        }
    }

    private static void copyGenerationMs(Map<String, Object> previous, Map<String, Object> data) {
        if (data.containsKey("generationMs")) {
            return;
        }
        if (previous == null) {
            return;
        }
        Object v = previous.get("generationMs");
        if (v instanceof Number n && n.longValue() >= 0) {
            data.put("generationMs", n.longValue());
        }
    }

    private static int pickWindow(Integer fromUsage, Map<String, Object> previous, int fallback) {
        if (fromUsage != null && fromUsage > 0) {
            return fromUsage;
        }
        int prev = intVal(previous, "contextWindow");
        if (prev > 0) {
            return prev;
        }
        return Math.max(0, fallback);
    }

    private static int pickLatest(Integer incoming, int previous) {
        if (incoming != null && incoming > 0) {
            return incoming;
        }
        return Math.max(0, previous);
    }

    private static int pickSnapshot(Integer incoming, int previous) {
        return incoming == null ? Math.max(0, previous) : Math.max(0, incoming);
    }

    private static double clampPercent(double v) {
        if (!Double.isFinite(v)) {
            return 0.0;
        }
        return Math.min(100.0, Math.max(0.0, v));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : Math.max(0, v);
    }

    private static int intVal(Map<String, Object> map, String key) {
        if (map == null) {
            return 0;
        }
        Object v = map.get(key);
        if (v instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        return 0;
    }

    private static double doubleVal(Map<String, Object> map, String key) {
        if (map == null) {
            return 0.0;
        }
        Object v = map.get(key);
        if (v instanceof Number n) {
            return clampPercent(n.doubleValue());
        }
        return 0.0;
    }

    private static boolean boolVal(Map<String, Object> map, String key) {
        if (map == null) {
            return false;
        }
        Object v = map.get(key);
        return v instanceof Boolean b && b;
    }
}
