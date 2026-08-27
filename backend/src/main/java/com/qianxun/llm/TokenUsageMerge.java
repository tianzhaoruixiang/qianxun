package com.qianxun.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 透传上游 usage，不自行估算 token。
 * <ul>
 *   <li>OpenAI：同轮多次请求做加法；占用取最近一次 prompt。</li>
 *   <li>Dashboard 会话快照（{@code sessionSnapshot}）：input/output/total 已是会话累计，
 *       只保留最新一帧；占用优先用 {@code context_used}/{@code context_percent}。</li>
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
        return data;
    }

    /** 流式中间帧：只刷新当前窗口占用，不把中间 API 调用加进本轮计费。 */
    private static Map<String, Object> mergeLiveOccupancy(
            Map<String, Object> previous,
            OpenAiCompatibleStreamClient.TokenUsage usage,
            int fallbackContextWindow
    ) {
        int prompt = nz(usage.promptTokens());
        int completion = nz(usage.completionTokens());
        int prevUsed = intVal(previous, "contextUsed");
        int prevOut = intVal(previous, "liveOutputTokens");
        int occupied;
        if (usage.contextUsed() != null && usage.contextUsed() > 0) {
            occupied = usage.contextUsed();
        } else if (prompt > 0) {
            occupied = prompt + completion;
        } else if (completion > 0 && prevUsed > 0) {
            occupied = Math.max(completion, prevUsed - prevOut + completion);
        } else {
            occupied = prevUsed;
        }
        int window = pickWindow(usage.contextWindow(), previous, fallbackContextWindow);
        int contextUsed = occupied > 0 ? occupied : intVal(previous, "contextUsed");
        double percent = window > 0 && contextUsed > 0
                ? clampPercent(contextUsed * 100.0 / window)
                : doubleVal(previous, "contextPercent");

        Map<String, Object> data = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
        data.put("contextWindow", window);
        data.put("contextUsed", contextUsed);
        data.put("contextPercent", percent);
        data.put("live", true);
        if (completion > 0) {
            data.put("liveOutputTokens", completion);
        }
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
        int contextUsed = pickLatest(usage.contextUsed(), prompt > 0 ? prompt : intVal(previous, "contextUsed"));
        double percent = usage.contextPercent() != null
                ? clampPercent(usage.contextPercent())
                : (window > 0 && contextUsed > 0 ? clampPercent(contextUsed * 100.0 / window) : 0.0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("promptTokens", sumPrompt);
        data.put("completionTokens", sumCompletion);
        data.put("totalTokens", sumTotal);
        data.put("contextWindow", window);
        data.put("contextUsed", contextUsed);
        data.put("contextPercent", percent);
        return data;
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
