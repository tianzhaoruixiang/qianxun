package com.qianxun.domain;

import java.time.Instant;

public record ChatSession(
        String id,
        String userId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        String agentCode,
        String hermesProfile,
        String agentName,
        /** 会话级长程目标 JSON，空字符串表示未设定 */
        String sessionGoal,
        /** task-* 子会话指向用户可见父会话；普通会话为空 */
        String parentSessionId
) {
    public ChatSession(String id, String userId, String title, Instant createdAt, Instant updatedAt) {
        this(id, userId, title, createdAt, updatedAt, "", "", "", "", "");
    }

    public ChatSession(
            String id,
            String userId,
            String title,
            Instant createdAt,
            Instant updatedAt,
            String agentCode,
            String hermesProfile,
            String agentName
    ) {
        this(id, userId, title, createdAt, updatedAt, agentCode, hermesProfile, agentName, "", "");
    }

    public ChatSession(
            String id,
            String userId,
            String title,
            Instant createdAt,
            Instant updatedAt,
            String agentCode,
            String hermesProfile,
            String agentName,
            String sessionGoal
    ) {
        this(id, userId, title, createdAt, updatedAt, agentCode, hermesProfile, agentName, sessionGoal, "");
    }
}
