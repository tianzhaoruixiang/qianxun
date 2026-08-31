package com.qianxun.web.dto;

import com.qianxun.service.stream.AgentTask;
import com.qianxun.service.stream.AgentTaskState;

public record AgentTaskResponse(
        String id,
        String state,
        String agentCode,
        String agentName,
        String parentRunId,
        String parentSessionId,
        String childSessionId,
        String childRunId,
        String message,
        String artifact,
        String error,
        long createdAtMs,
        long updatedAtMs
) {
    public static AgentTaskResponse from(AgentTask task) {
        if (task == null) {
            return null;
        }
        AgentTaskState st = task.state();
        return new AgentTaskResponse(
                task.id(),
                st == null ? "" : st.wire(),
                task.agentCode(),
                task.agentName(),
                task.parentRunId(),
                task.parentSessionId(),
                task.childSessionId(),
                blankToNull(task.childRunId()),
                task.message(),
                blankToNull(task.artifact()),
                blankToNull(task.error()),
                task.createdAtMs(),
                task.updatedAtMs()
        );
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
