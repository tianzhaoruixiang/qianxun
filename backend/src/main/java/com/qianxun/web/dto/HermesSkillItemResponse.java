package com.qianxun.web.dto;

public record HermesSkillItemResponse(
        String name,
        String description,
        String category,
        boolean enabled,
        String provenance
) {}
