package com.qianxun.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qianxun.domain.ChatSession;

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
        String lastMessagePreview
) {
    public static ChatSessionResponse basic(ChatSession s) {
        return new ChatSessionResponse(s.id(), s.title(), s.createdAt(), s.updatedAt(), null, null);
    }
}
