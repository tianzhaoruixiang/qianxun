package com.qianxun.service.stream;

/**
 * 对齐 A2A TaskState 的内部任务状态。对外暴露时用小写 JSON 值。
 */
public enum AgentTaskState {
    SUBMITTED,
    WORKING,
    INPUT_REQUIRED,
    COMPLETED,
    FAILED,
    CANCELED,
    REJECTED;

    public String wire() {
        return switch (this) {
            case SUBMITTED -> "submitted";
            case WORKING -> "working";
            case INPUT_REQUIRED -> "input-required";
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case CANCELED -> "canceled";
            case REJECTED -> "rejected";
        };
    }

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED || this == REJECTED;
    }

    public static AgentTaskState parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.trim().toLowerCase().replace('_', '-');
        return switch (n) {
            case "submitted" -> SUBMITTED;
            case "working" -> WORKING;
            case "input-required", "input_required" -> INPUT_REQUIRED;
            case "completed" -> COMPLETED;
            case "failed" -> FAILED;
            case "canceled", "cancelled" -> CANCELED;
            case "rejected" -> REJECTED;
            default -> null;
        };
    }

    public boolean canTransitionTo(AgentTaskState next) {
        if (next == null || this == next) {
            return false;
        }
        if (terminal()) {
            return false;
        }
        return switch (this) {
            case SUBMITTED -> next == WORKING || next == REJECTED || next == CANCELED;
            case WORKING -> next == COMPLETED || next == FAILED || next == CANCELED || next == INPUT_REQUIRED;
            case INPUT_REQUIRED -> next == WORKING || next == CANCELED || next == REJECTED;
            default -> false;
        };
    }

    /** 投影到现有 crew 工具 status。 */
    public String crewStatus() {
        return switch (this) {
            case SUBMITTED, INPUT_REQUIRED -> "awaiting";
            case WORKING -> "running";
            case COMPLETED, CANCELED -> "completed";
            case FAILED, REJECTED -> "error";
        };
    }
}
