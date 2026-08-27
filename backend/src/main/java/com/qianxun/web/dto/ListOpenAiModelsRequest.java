package com.qianxun.web.dto;

public record ListOpenAiModelsRequest(
        String openaiBaseUrl,
        String openaiApiKey
) {}
