package com.qianxun.web.dto;

public record AgentRegistryResponse(
        String id,
        String code,
        String name,
        String category,
        String description,
        String icon,
        String modelCode,
        int priority,
        boolean enabled
) {}

