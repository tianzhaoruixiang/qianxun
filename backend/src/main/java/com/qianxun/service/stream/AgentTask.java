package com.qianxun.service.stream;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class AgentTask {

    public static final String SESSION_PREFIX = "task-";

    private final String id;
    private final String userId;
    private final String parentRunId;
    private final String parentSessionId;
    private final String agentCode;
    private final String agentName;
    private final String childSessionId;
    private final String message;
    private final long createdAtMs;
    private volatile AgentTaskState state;
    private volatile String childRunId = "";
    private volatile String artifact = "";
    private volatile String error = "";
    private volatile String agentIcon = "";
    private volatile long updatedAtMs;
    private final CountDownLatch terminalLatch = new CountDownLatch(1);

    public AgentTask(
            String userId,
            String parentRunId,
            String parentSessionId,
            String agentCode,
            String agentName,
            String message
    ) {
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.userId = userId == null ? "" : userId;
        this.parentRunId = parentRunId == null ? "" : parentRunId;
        this.parentSessionId = parentSessionId == null ? "" : parentSessionId;
        this.agentCode = agentCode == null ? "" : agentCode.trim();
        this.agentName = agentName == null ? "" : agentName.trim();
        this.message = message == null ? "" : message;
        this.childSessionId = SESSION_PREFIX + this.id;
        this.createdAtMs = System.currentTimeMillis();
        this.updatedAtMs = this.createdAtMs;
        this.state = AgentTaskState.SUBMITTED;
    }

    public String id() { return id; }
    public String userId() { return userId; }
    public String parentRunId() { return parentRunId; }
    public String parentSessionId() { return parentSessionId; }
    public String agentCode() { return agentCode; }
    public String agentName() { return agentName; }
    public String agentIcon() { return agentIcon; }
    public String childSessionId() { return childSessionId; }
    public String message() { return message; }
    public String childRunId() { return childRunId; }
    public String artifact() { return artifact; }
    public String error() { return error; }
    public long createdAtMs() { return createdAtMs; }
    public long updatedAtMs() { return updatedAtMs; }

    public synchronized AgentTaskState state() {
        return state;
    }

    public void setChildRunId(String runId) {
        this.childRunId = runId == null ? "" : runId;
    }

    public void setAgentIcon(String icon) {
        this.agentIcon = icon == null ? "" : icon.trim();
    }

    public synchronized boolean transition(AgentTaskState next) {
        if (!state.canTransitionTo(next)) {
            return false;
        }
        state = next;
        updatedAtMs = System.currentTimeMillis();
        if (state.terminal()) {
            terminalLatch.countDown();
        }
        return true;
    }

    /**
     * 等到终态。{@code timeoutMs <= 0} 表示不限时。
     * {@code abort} 为真时提前返回（未必已终态）。
     */
    public boolean awaitTerminal(long timeoutMs, BooleanSupplier abort) {
        long deadline = timeoutMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + timeoutMs;
        try {
            while (!state().terminal()) {
                if (abort != null && abort.getAsBoolean()) {
                    return state().terminal();
                }
                long now = System.currentTimeMillis();
                if (now >= deadline) {
                    return state().terminal();
                }
                long slice = Math.min(400L, Math.max(1L, deadline - now));
                if (terminalLatch.await(slice, TimeUnit.MILLISECONDS)) {
                    return state().terminal();
                }
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return state().terminal();
        }
    }

    public synchronized void setArtifact(String text) {
        this.artifact = text == null ? "" : text;
        updatedAtMs = System.currentTimeMillis();
    }

    public synchronized void setError(String text) {
        this.error = text == null ? "" : text;
        updatedAtMs = System.currentTimeMillis();
    }

    public static boolean isTaskSession(String sessionId) {
        return sessionId != null && sessionId.startsWith(SESSION_PREFIX);
    }
}
