package com.qianxun.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qianxun.service.stream.ChatRun;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunSummaryResponse(
        String runId,
        String traceId,
        String sessionId,
        String userId,
        String status,
        String assistantMessageId,
        long lastSeq,
        Boolean cancelRequested,
        String hermesProfile,
        String agentCode,
        String modelCode,
        long startedAtMs,
        long lastEventAtMs,
        int toolCallCount,
        int delegationCount,
        String contentPreview
) {
    public static RunSummaryResponse from(ChatRun run) {
        if (run == null) {
            return null;
        }
        String preview = run.contentSnapshot();
        if (preview != null && preview.length() > 120) {
            preview = preview.substring(0, 120) + "…";
        }
        return new RunSummaryResponse(
                run.runId(),
                run.traceId(),
                run.sessionId(),
                run.userId(),
                run.status().name(),
                blankToNull(run.assistantMessageId()),
                run.lastSeq(),
                run.isCancelRequested() ? Boolean.TRUE : null,
                blankToNull(run.hermesProfile()),
                blankToNull(run.agentCode()),
                blankToNull(run.modelCode()),
                run.startedAtMs(),
                run.lastEventAtMs(),
                run.toolCallCount(),
                run.delegationCount(),
                blankToNull(preview)
        );
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
