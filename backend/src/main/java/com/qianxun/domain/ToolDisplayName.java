package com.qianxun.domain;

public record ToolDisplayName(
        String toolCode,
        String displayName,
        int sortOrder
) {}
