package com.qianxun.web.dto;

import jakarta.validation.constraints.NotBlank;

public record StreamChatRequest(
        @NotBlank String content,
        /** "quick"（默认）或 "deep"（深度思考） */
        String thinkingMode
) {
    public static final String MODE_QUICK = "quick";
    public static final String MODE_DEEP  = "deep";

    public boolean isDeep() {
        return MODE_DEEP.equalsIgnoreCase(thinkingMode);
    }
}
