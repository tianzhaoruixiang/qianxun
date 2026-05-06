package com.qianxun.nlu;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简模板渲染器：支持 {{name}} 与 {{name|default}}，
 * 缺失值时使用 default；既无值也无 default 时输出空串。
 */
public final class PromptTemplateRenderer {

    private static final Pattern PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_\\-]*)\\s*(?:\\|\\s*([^}]*?))?\\s*}}");

    private PromptTemplateRenderer() {
    }

    public static String render(String template, Map<String, Object> values) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher m = PATTERN.matcher(template);
        final StringBuilder out = new StringBuilder(template.length() + 16);
        while (m.find()) {
            String name = m.group(1);
            String def = m.group(2) == null ? "" : m.group(2).trim();
            Object raw = values == null ? null : values.get(name);
            String value;
            if (raw == null || (raw instanceof CharSequence cs && cs.toString().trim().isEmpty())) {
                value = def;
            } else {
                value = String.valueOf(raw);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
