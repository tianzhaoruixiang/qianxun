package com.qianxun.web.dto;

public record UpdateSystemSettingsRequest(
        String systemName,
        String claudeChatModel,
        String openaiBaseUrl,
        String openaiApiKey
) {}
