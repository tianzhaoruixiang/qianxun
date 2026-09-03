package com.qianxun.web.dto;

public record UpdateSystemSettingsRequest(
        String systemName,
        String claudeChatModel,
        String openaiBaseUrl,
        String openaiApiKey,
        String mem0EmbedderModel,
        Integer mem0EmbeddingDims
) {
    public UpdateSystemSettingsRequest(
            String systemName,
            String claudeChatModel,
            String openaiBaseUrl,
            String openaiApiKey
    ) {
        this(systemName, claudeChatModel, openaiBaseUrl, openaiApiKey, null, null);
    }
}
