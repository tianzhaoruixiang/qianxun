package com.qianxun.web.dto;

public record UpsertModelRegistryRequest(
        String code,
        String name,
        String provider,
        String baseUrl,
        Integer contextWindow,
        Integer maxTokens,
        Boolean enabled
) {}

