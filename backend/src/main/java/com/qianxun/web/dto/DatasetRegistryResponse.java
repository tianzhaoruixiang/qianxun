package com.qianxun.web.dto;

public record DatasetRegistryResponse(
        String id,
        String code,
        String name,
        String description,
        String sourceType,
        String sourceRef,
        int docCount,
        boolean enabled
) {}

