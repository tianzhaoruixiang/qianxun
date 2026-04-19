package com.qianxun.nlu;

/**
 * 从模型输出中提取 JSON 对象文本（兼容 ```json ... ``` 包裹等情况）。
 */
final class JsonPayloadExtractor {

    private JsonPayloadExtractor() {
    }

    static String extractJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = stripFences(text);
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1).trim();
    }

    private static String stripFences(String text) {
        String t = text.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        t = t.substring(3).trim();
        if (t.startsWith("json")) {
            t = t.substring("json".length()).trim();
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3).trim();
        }
        return t;
    }
}
