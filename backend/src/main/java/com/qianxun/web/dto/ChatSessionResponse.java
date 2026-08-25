package com.qianxun.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qianxun.domain.ChatSession;
import com.qianxun.service.ChatGoalInvocation;
import com.qianxun.service.SessionAgentLabels;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatSessionResponse(
        String id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        /** 仅在会话列表接口填充 */
        Integer messageCount,
        /** 仅在会话列表接口填充：最后一条消息正文预览 */
        String lastMessagePreview,
        /** 注册智能体 code；数智干警为空 */
        String agentCode,
        /** 聊天时使用的 Hermes profile */
        String hermesProfile,
        /** 展示用中文名（数智干警 / 注册表 name） */
        String agentName,
        /** 当前会话长程目标；未设定时为空 */
        SessionGoalResponse goal,
        /** 该会话是否有进行中的流式生成 */
        Boolean streaming
) {
    public static ChatSessionResponse basic(ChatSession s) {
        String name = SessionAgentLabels.displayName(s.agentCode(), s.hermesProfile(), s.agentName(), null);
        return new ChatSessionResponse(
                s.id(), s.title(), s.createdAt(), s.updatedAt(), null, null,
                emptyToNull(s.agentCode()), emptyToNull(s.hermesProfile()), name,
                SessionGoalResponse.from(ChatGoalInvocation.parseJson(s.sessionGoal())),
                null
        );
    }

    public ChatSessionResponse withStreaming(boolean streaming) {
        return new ChatSessionResponse(
                id, title, createdAt, updatedAt, messageCount, lastMessagePreview,
                agentCode, hermesProfile, agentName, goal, streaming
        );
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
