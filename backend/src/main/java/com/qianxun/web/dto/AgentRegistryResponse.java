package com.qianxun.web.dto;

/**
 * 智能体注册表 API 返回（含可选系统提示模板，供编辑表单使用）。
 */
public record AgentRegistryResponse(
        String id,
        String code,
        String name,
        String category,
        String description,
        String icon,
        String modelCode,
        String promptTemplate,
        int priority,
        boolean enabled
) {}

