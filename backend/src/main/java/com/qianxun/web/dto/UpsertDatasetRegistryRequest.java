package com.qianxun.web.dto;

public record UpsertDatasetRegistryRequest(
        String code,
        String name,
        String description,
        String sourceType,
        String sourceRef,
        Integer docCount,
        Boolean enabled
) {}

