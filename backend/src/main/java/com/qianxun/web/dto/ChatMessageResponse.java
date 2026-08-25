package com.qianxun.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ChatMessageResponse(
        String id,
        String sessionId,
        String role,
        String content,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("toolCalls")
        String toolCallsJson,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("usage")
        String usageJson,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("suggestions")
        String suggestionsJson,
        Instant createdAt,
        /** completed | streaming | cancelled | error */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        String status,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        String runId
) {}
