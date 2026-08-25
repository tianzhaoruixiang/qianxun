package com.qianxun.domain;

import java.time.Instant;

public record AgentRegistryItem(
        String id,
        String code,
        String name,
        String category,
        String description,
        String icon,
        String modelCode,
        /** 进入对话时空态欢迎主标题；空则用前端默认「你好，我是 …」 */
        String welcomeTitle,
        /** 欢迎副文案（简介）；空则用全局默认能力描述 */
        String welcomeIntro,
        /** 预置用户话术一；空表示未配置 */
        String presetChat1,
        String presetChat2,
        String presetChat3,
        /** 历史列，upsert 不再写入；对话一律走 Hermes profile。 */
        String apiBaseUrl,
        /** 历史列，upsert 不再写入。 */
        String upstreamModel,
        /** 历史列，upsert 不再写入。 */
        String apiKey,
        /** 绑定的 Hermes profile 名（对应 Desktop /api/profiles） */
        String hermesProfile,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
