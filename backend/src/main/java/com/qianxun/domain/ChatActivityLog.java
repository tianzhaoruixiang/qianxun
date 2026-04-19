package com.qianxun.domain;

import com.qianxun.context.UserContext;
import java.time.Instant;

public record ChatActivityLog(
        String id,
        String userId,
        String sessionId,
        String userMessageId,
        String assistantMessageId,
        String userContent,
        String nluIntent,
        String nluScenarioCode,
        String nluScenarioName,
        String nluAgentSkill,
        Double nluConfidence,
        String nluSlots,
        String nluMissingSlots,
        String nluReasoning,
        String nluRawResponse,
        String llmEndpoint,
        String llmModel,
        String llmRequestJson,
        String llmResponseText,
        String status,
        String errorMessage,
        String thinkingMode,
        String thinkContent,
        Long nluDurationMs,
        Long llmDurationMs,
        Long totalDurationMs,
        Instant createdAt
) {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR   = "error";
    public static final String STATUS_MOCK    = "mock";

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String userId = UserContext.DEFAULT_USER_ID;
        private String sessionId;
        private String userMessageId;
        private String assistantMessageId;
        private String userContent;
        private String nluIntent;
        private String nluScenarioCode;
        private String nluScenarioName;
        private String nluAgentSkill;
        private Double nluConfidence;
        private String nluSlots;
        private String nluMissingSlots;
        private String nluReasoning;
        private String nluRawResponse;
        private String llmEndpoint;
        private String llmModel;
        private String llmRequestJson;
        private String llmResponseText;
        private String status = STATUS_SUCCESS;
        private String errorMessage;
        private String thinkingMode = "quick";
        private String thinkContent;
        private Long nluDurationMs;
        private Long llmDurationMs;
        private Long totalDurationMs;
        private Instant createdAt;

        private Builder() {}

        public Builder id(String v)                { this.id = v; return this; }
        public Builder userId(String v)            { this.userId = v; return this; }
        public Builder sessionId(String v)         { this.sessionId = v; return this; }
        public Builder userMessageId(String v)     { this.userMessageId = v; return this; }
        public Builder assistantMessageId(String v){ this.assistantMessageId = v; return this; }
        public Builder userContent(String v)       { this.userContent = v; return this; }
        public Builder nluIntent(String v)         { this.nluIntent = v; return this; }
        public Builder nluScenarioCode(String v)   { this.nluScenarioCode = v; return this; }
        public Builder nluScenarioName(String v)   { this.nluScenarioName = v; return this; }
        public Builder nluAgentSkill(String v)     { this.nluAgentSkill = v; return this; }
        public Builder nluConfidence(Double v)     { this.nluConfidence = v; return this; }
        public Builder nluSlots(String v)          { this.nluSlots = v; return this; }
        public Builder nluMissingSlots(String v)   { this.nluMissingSlots = v; return this; }
        public Builder nluReasoning(String v)      { this.nluReasoning = v; return this; }
        public Builder nluRawResponse(String v)    { this.nluRawResponse = v; return this; }
        public Builder llmEndpoint(String v)       { this.llmEndpoint = v; return this; }
        public Builder llmModel(String v)          { this.llmModel = v; return this; }
        public Builder llmRequestJson(String v)    { this.llmRequestJson = v; return this; }
        public Builder llmResponseText(String v)   { this.llmResponseText = v; return this; }
        public Builder status(String v)            { this.status = v; return this; }
        public Builder errorMessage(String v)      { this.errorMessage = v; return this; }
        public Builder thinkingMode(String v)      { this.thinkingMode = v; return this; }
        public Builder thinkContent(String v)      { this.thinkContent = v; return this; }
        public Builder nluDurationMs(Long v)       { this.nluDurationMs = v; return this; }
        public Builder llmDurationMs(Long v)       { this.llmDurationMs = v; return this; }
        public Builder totalDurationMs(Long v)     { this.totalDurationMs = v; return this; }
        public Builder createdAt(Instant v)        { this.createdAt = v; return this; }

        public ChatActivityLog build() {
            return new ChatActivityLog(
                    id, userId, sessionId, userMessageId, assistantMessageId,
                    userContent, nluIntent, nluScenarioCode, nluScenarioName, nluAgentSkill,
                    nluConfidence, nluSlots, nluMissingSlots, nluReasoning, nluRawResponse,
                    llmEndpoint, llmModel, llmRequestJson, llmResponseText,
                    status, errorMessage, thinkingMode, thinkContent,
                    nluDurationMs, llmDurationMs, totalDurationMs,
                    createdAt == null ? Instant.now() : createdAt
            );
        }
    }
}
