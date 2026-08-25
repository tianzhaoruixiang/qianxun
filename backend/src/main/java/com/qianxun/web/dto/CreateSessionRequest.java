package com.qianxun.web.dto;

public record CreateSessionRequest(
        String title,
        String agentCode,
        String hermesProfile,
        String agentName
) {
    public CreateSessionRequest(String title) {
        this(title, null, null, null);
    }
}
