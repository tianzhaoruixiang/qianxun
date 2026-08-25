package com.qianxun.web.dto;

public record UpsertAgentRegistryRequest(
        String code,
        String name,
        String category,
        String description,
        String icon,
        String modelCode,
        /** 欢迎主标题；null 表示更新时保留 */
        String welcomeTitle,
        /** 欢迎简介；null 表示更新时保留 */
        String welcomeIntro,
        /** 预置对话一；null 表示更新时保留 */
        String presetChat1,
        String presetChat2,
        String presetChat3,
        /** 绑定的 Hermes profile；null 表示更新时保留 */
        String hermesProfile,
        /**
         * Hermes profile 的 SOUL.md，必填。
         */
        String soulMd,
        Integer priority,
        Boolean enabled
) {}
