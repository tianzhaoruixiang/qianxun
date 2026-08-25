package com.qianxun.service;

import java.util.Locale;

/**
 * 历史会话智能体展示名：优先注册表中文名，默认 Hermes 归入「数智干警」，
 * 旧数据缺字段不报错。
 */
public final class SessionAgentLabels {

    public static final String DIGITAL_OFFICER = "数智干警";
    public static final String UNCATEGORIZED = "未分类";

    private SessionAgentLabels() {}

    public static boolean isDefaultProfile(String hermesProfile) {
        if (hermesProfile == null || hermesProfile.isBlank()) {
            return true;
        }
        String p = hermesProfile.trim().toLowerCase(Locale.ROOT);
        return "default".equals(p) || "hermes-agent".equals(p);
    }

    public static boolean looksLikeTechnicalId(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.UnicodeScript.of(name.charAt(i)) == Character.UnicodeScript.HAN) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param registryName 当前注册表 name（可空）
     * @param storedName   会话落库时的展示名快照（可空）
     */
    public static String displayName(String agentCode, String hermesProfile, String storedName, String registryName) {
        String fromRegistry = trimToNull(registryName);
        if (fromRegistry != null && !looksLikeTechnicalId(fromRegistry)) {
            return fromRegistry;
        }
        String stored = trimToNull(storedName);
        if (stored != null && !looksLikeTechnicalId(stored)) {
            return stored;
        }
        if (fromRegistry != null) {
            return fromRegistry;
        }
        boolean noAgent = agentCode == null || agentCode.isBlank();
        if (noAgent && isDefaultProfile(hermesProfile)) {
            return DIGITAL_OFFICER;
        }
        if (stored != null) {
            if (equalsIgnore(stored, agentCode) || equalsIgnore(stored, hermesProfile) || looksLikeTechnicalId(stored)) {
                return isDefaultProfile(hermesProfile) ? DIGITAL_OFFICER : UNCATEGORIZED;
            }
            return stored;
        }
        if (noAgent) {
            return isDefaultProfile(hermesProfile) ? DIGITAL_OFFICER : UNCATEGORIZED;
        }
        return UNCATEGORIZED;
    }

    public static String snapshotName(String agentCode, String hermesProfile, String requestedName, String registryName) {
        String requested = trimToNull(requestedName);
        if (requested != null && !looksLikeTechnicalId(requested)) {
            return requested;
        }
        return displayName(agentCode, hermesProfile, requested, registryName);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean equalsIgnore(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
