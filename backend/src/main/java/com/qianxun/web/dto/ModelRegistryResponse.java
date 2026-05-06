package com.qianxun.web.dto;

public record ModelRegistryResponse(
        String id,
        String code,
        String name,
        String provider,
        String baseUrl,
        int contextWindow,
        int maxTokens,
        boolean enabled
) {}

