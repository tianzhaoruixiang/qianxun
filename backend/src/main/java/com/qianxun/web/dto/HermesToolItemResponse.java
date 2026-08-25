package com.qianxun.web.dto;

public record HermesToolItemResponse(
        String name,
        String displayName,
        String iconKind,
        boolean enabled
) {}
