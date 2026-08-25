package com.qianxun.service;

import java.util.List;

/**
 * 数智干警欢迎页三条预置对话：存 {@code ui_string_config}，管理员在智能体市场改。
 */
public final class WelcomeOfficerPresets {

    public static final String KEY_1 = "welcome.preset_chat_1";
    public static final String KEY_2 = "welcome.preset_chat_2";
    public static final String KEY_3 = "welcome.preset_chat_3";
    public static final int MAX_CHARS = 2000;

    public static final String DEFAULT_1 =
            "请检索过去24小时与“低空经济”相关的重点政策动态，并按地区汇总。";
    public static final String DEFAULT_2 =
            "帮我梳理本周“人工智能芯片”领域的重要新闻，标注来源与发布时间。";
    public static final String DEFAULT_3 =
            "请查询“跨境电商”近7天舆情热点，给出风险点和机会点。";

    private WelcomeOfficerPresets() {}

    public static String clip(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() > MAX_CHARS) {
            return t.substring(0, MAX_CHARS);
        }
        return t;
    }

    /**
     * 已保存过任意一条则原样返回（空槽不展示）；
     * 三槽都空时回退到推荐问法前三条，再不够用内置默认。
     */
    public static String[] resolve(String stored1, String stored2, String stored3, List<String> suggested) {
        String a = clip(stored1);
        String b = clip(stored2);
        String c = clip(stored3);
        if (!a.isEmpty() || !b.isEmpty() || !c.isEmpty()) {
            return new String[]{a, b, c};
        }
        String[] out = new String[]{"", "", ""};
        int i = 0;
        if (suggested != null) {
            for (String s : suggested) {
                if (i >= 3) {
                    break;
                }
                String t = clip(s);
                if (!t.isEmpty()) {
                    out[i++] = t;
                }
            }
        }
        if (i == 0) {
            return new String[]{DEFAULT_1, DEFAULT_2, DEFAULT_3};
        }
        return out;
    }

    public static String[] defaults() {
        return new String[]{DEFAULT_1, DEFAULT_2, DEFAULT_3};
    }
}
