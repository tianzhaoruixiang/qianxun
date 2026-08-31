package com.qianxun.llm;

import java.util.Locale;

/**
 * 上游 {@code /models} 常只返回 id，不含窗口。对产品里常用的公开模型给只读兜底，
 * 避免欢迎页/选择器在进入对话前一直空白。实时声明与注册表仍优先。
 */
public final class KnownModelContextWindows {

    private KnownModelContextWindows() {
    }

    public static int lookup(String modelId) {
        String key = normalize(modelId);
        if (key.isEmpty()) {
            return 0;
        }
        if (key.startsWith("qwen3.6-plus") || key.startsWith("qwen3-6-plus")
                || key.startsWith("qwen3.5-plus") || key.equals("qwen3-plus")) {
            return 1_000_000;
        }
        if (key.startsWith("claude-sonnet-4-5") || key.startsWith("claude-sonnet-4-6")
                || key.equals("sonnet")) {
            return 200_000;
        }
        if (key.startsWith("claude-opus-4") || key.equals("opus")) {
            return 200_000;
        }
        if (key.startsWith("claude-haiku-4") || key.equals("haiku")) {
            return 200_000;
        }
        return 0;
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        int slash = v.indexOf('/');
        if (slash > 0 && slash < v.length() - 1) {
            String prefix = v.substring(0, slash);
            if ("openai".equals(prefix) || "anthropic".equals(prefix) || "litellm".equals(prefix)) {
                return v.substring(slash + 1);
            }
        }
        return v;
    }
}
