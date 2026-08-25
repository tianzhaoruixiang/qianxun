package com.qianxun.storage;

import com.qianxun.config.QianxunProperties;

/**
 * 用户文档公开下载地址。智能体（Hermes）用 {@code qianxun.minio.public-base-url} 拼出可直连的 URL。
 */
public final class FilePublicLinks {

    public static final String PATH_PREFIX = "/QianXunService/data/files/public/";

    private FilePublicLinks() {}

    public static String url(QianxunProperties properties, String publicToken) {
        if (publicToken == null || publicToken.isBlank()) {
            return "";
        }
        String base = properties == null || properties.getMinio() == null
                ? ""
                : nullToEmpty(properties.getMinio().getPublicBaseUrl());
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + PATH_PREFIX + publicToken.trim();
    }

    public static String relativePath(String publicToken) {
        if (publicToken == null || publicToken.isBlank()) {
            return "";
        }
        return PATH_PREFIX + publicToken.trim();
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
