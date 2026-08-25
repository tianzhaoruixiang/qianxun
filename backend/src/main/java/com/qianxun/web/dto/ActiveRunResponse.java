package com.qianxun.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActiveRunResponse(
        String runId,
        String traceId,
        String sessionId,
        String status,
        String assistantMessageId,
        long lastSeq,
        Boolean cancelRequested,
        String hermesProfile,
        String agentCode,
        String modelCode,
        long startedAtMs,
        int toolCallCount,
        int delegationCount
) {
    public static ActiveRunResponse from(com.qianxun.service.stream.ChatRun run) {
        if (run == null) {
            return null;
        }
        return new ActiveRunResponse(
                run.runId(),
                blankToNull(run.traceId()),
                run.sessionId(),
                run.status().name(),
                blankToNull(run.assistantMessageId()),
                run.lastSeq(),
                run.isCancelRequested() ? Boolean.TRUE : null,
                blankToNull(run.hermesProfile()),
                blankToNull(run.agentCode()),
                blankToNull(run.modelCode()),
                run.startedAtMs(),
                run.toolCallCount(),
                run.delegationCount()
        );
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
