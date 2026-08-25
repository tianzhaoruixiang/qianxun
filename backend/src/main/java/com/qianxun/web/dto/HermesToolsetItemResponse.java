package com.qianxun.web.dto;

import java.util.List;

public record HermesToolsetItemResponse(
        String name,
        String label,
        String description,
        String platform,
        String platformLabel,
        boolean enabled,
        boolean configured,
        List<HermesToolItemResponse> tools
) {}
