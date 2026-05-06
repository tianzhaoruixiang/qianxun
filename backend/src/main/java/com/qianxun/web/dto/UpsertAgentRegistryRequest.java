package com.qianxun.web.dto;

public record UpsertAgentRegistryRequest(
        String code,
        String name,
        String category,
        String description,
        String icon,
        String modelCode,
        String promptTemplate,
        Integer priority,
        Boolean enabled
) {}

