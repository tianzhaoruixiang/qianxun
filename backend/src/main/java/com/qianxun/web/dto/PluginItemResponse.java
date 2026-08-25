package com.qianxun.web.dto;

import java.util.Map;

public record PluginItemResponse(
        String name,
        String path,
        String version,
        boolean enabled,
        String description,
        Map<String, Object> manifest
) {}
