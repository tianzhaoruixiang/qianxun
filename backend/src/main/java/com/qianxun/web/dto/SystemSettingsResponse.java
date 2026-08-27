package com.qianxun.web.dto;

/**
 * 系统展示名与 LiteLLM 上游 OpenAI Compatible 连接（管理员可改）。
 * {@code openaiApiKeyMasked} 不含完整密钥。
 */
public record SystemSettingsResponse(
        String systemName,
        String claudeChatModel,
        String openaiBaseUrl,
        String openaiApiKeyMasked,
        boolean openaiApiKeyConfigured,
        Integer claudeChatContextWindow
) {
    public static SystemSettingsResponse brandOnly(String systemName) {
        return new SystemSettingsResponse(systemName == null ? "" : systemName, "", "", "", false, null);
    }
}
