package com.qianxun.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;

/**
 * 从助手正文中解析约定格式的实体 JSON 代码块（语言标记：qianxun-entities），并返回去除该段后的正文。
 */
public final class EntityBlockParser {

    private static final String MARKER = "```qianxun-entities";

    private EntityBlockParser() {}

    public record Result(String cleanContent, JsonNode entitiesArray) {}

    /**
     * 取最后一次出现的实体块（避免误匹配示例中的 fence）。
     */
    public static Result parse(String raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isBlank()) {
            return new Result("", emptyArray(objectMapper));
        }
        String text = raw;
        int idx = lastIndexOfIgnoreCase(text, MARKER);
        if (idx < 0) {
            return new Result(text.stripTrailing(), emptyArray(objectMapper));
        }
        int firstLineEnd = text.indexOf('\n', idx);
        if (firstLineEnd < 0) {
            return new Result(text.substring(0, idx).stripTrailing(), emptyArray(objectMapper));
        }
        int jsonStart = firstLineEnd + 1;
        int closeFence = text.indexOf("```", jsonStart);
        if (closeFence < 0) {
            return new Result(text.substring(0, idx).stripTrailing(), emptyArray(objectMapper));
        }
        String inner = text.substring(jsonStart, closeFence).trim();
        String clean = text.substring(0, idx).stripTrailing();
        JsonNode arr;
        try {
            JsonNode n = objectMapper.readTree(inner);
            arr = n != null && n.isArray() ? n : emptyArray(objectMapper);
        } catch (Exception e) {
            arr = emptyArray(objectMapper);
        }
        return new Result(clean, normalizeEntities(arr, objectMapper));
    }

    private static JsonNode normalizeEntities(JsonNode arr, ObjectMapper om) {
        ArrayNode out = om.createArrayNode();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode el : arr) {
            if (!el.isObject()) {
                continue;
            }
            String name = textOrEmpty(el.path("name"));
            if (name.isBlank()) {
                continue;
            }
            String cat = textOrEmpty(el.path("category")).toLowerCase(Locale.ROOT);
            if (!isAllowedCategory(cat)) {
                cat = "thing";
            }
            ObjectNode o = om.createObjectNode();
            o.put("name", name.strip());
            o.put("category", cat);
            putIfText(o, "type", el.path("type"));
            putIfText(o, "nameEn", el.path("nameEn"));
            putIfText(o, "description", el.path("description"));
            out.add(o);
        }
        return out;
    }

    private static void putIfText(ObjectNode o, String key, JsonNode v) {
        if (v != null && v.isTextual()) {
            String t = v.asText("").strip();
            if (!t.isEmpty()) {
                o.put(key, t.length() > 240 ? t.substring(0, 240) + "…" : t);
            }
        }
    }

    private static String textOrEmpty(JsonNode n) {
        return n == null || !n.isTextual() ? "" : n.asText("");
    }

    private static boolean isAllowedCategory(String c) {
        return c.equals("person") || c.equals("time") || c.equals("location")
                || c.equals("org") || c.equals("event") || c.equals("thing");
    }

    private static ArrayNode emptyArray(ObjectMapper om) {
        return om.createArrayNode();
    }

    private static int lastIndexOfIgnoreCase(String hay, String needle) {
        String h = hay.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        return h.lastIndexOf(n);
    }
}
