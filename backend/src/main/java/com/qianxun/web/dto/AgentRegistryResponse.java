package com.qianxun.web.dto;

/**
 * 智能体注册表 API 返回。
 */
public record AgentRegistryResponse(
        String id,
        String code,
        String name,
        String category,
        String description,
        String icon,
        String modelCode,
        String welcomeTitle,
        String welcomeIntro,
        String presetChat1,
        String presetChat2,
        String presetChat3,
        String hermesProfile,
        int priority,
        boolean enabled
) {}
