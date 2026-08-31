package com.qianxun.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubmitAgentTaskRequest(
        String agentCode,
        String message,
        String parentRunId,
        String parentSessionId,
        /** 默认 true：阻塞到子任务终态再返回，便于干警汇总。 */
        @JsonProperty("wait") Boolean awaitCompletion
) {
    public SubmitAgentTaskRequest(String agentCode, String message, String parentRunId, String parentSessionId) {
        this(agentCode, message, parentRunId, parentSessionId, Boolean.TRUE);
    }

    public boolean waitForCompletion() {
        return awaitCompletion == null || Boolean.TRUE.equals(awaitCompletion);
    }
}
