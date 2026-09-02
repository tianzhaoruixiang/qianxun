package com.qianxun.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QianXunServiceChatStreamDelegationTest {

    @Test
    void delegationUpdateOmitsNullTaskIndex() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolCallId", "call_1");
        data.put("status", "running");
        Map<String, Object> payload = QianXunServiceChatStream.delegationUpdatePayload(data, "Agent");
        assertThat(payload.get("toolCallId")).isEqualTo("call_1");
        assertThat(payload.get("toolName")).isEqualTo("Agent");
        assertThat(payload).doesNotContainKey("taskIndex");
        assertThat(payload.get("delegationId")).isEqualTo("");
        assertThat(payload.get("summary")).isEqualTo("");
    }

    @Test
    void delegationUpdateKeepsTaskIndexWhenPresent() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolCallId", "call_2");
        data.put("taskIndex", 1);
        data.put("summary", "调研普京");
        Map<String, Object> payload = QianXunServiceChatStream.delegationUpdatePayload(data, "delegate_task");
        assertThat(payload.get("taskIndex")).isEqualTo(1);
        assertThat(payload.get("summary")).isEqualTo("调研普京");
    }

    @Test
    void toolCallEventFromPayloadKeepsCrewIdentity() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", "abc123");
        payload.put("toolName", "delegate_to_agent");
        payload.put("displayName", "法务助手");
        payload.put("iconKind", "agent");
        payload.put("agentCode", "legal");
        payload.put("subagent", true);
        payload.put("childSessionId", "task-abc123");
        payload.put("seq", 9L);
        payload.put("sessionId", "parent-sess");
        var tc = QianXunServiceChatStream.toolCallEventFromPayload(payload);
        assertThat(tc.toolCallId()).isEqualTo("abc123");
        assertThat(tc.functionName()).isEqualTo("delegate_to_agent");
        assertThat(tc.details().get("displayName")).isEqualTo("法务助手");
        assertThat(tc.details()).doesNotContainKey("seq");
        assertThat(tc.details()).doesNotContainKey("sessionId");
        assertThat(tc.details().get("childSessionId")).isEqualTo("task-abc123");
    }

    @Test
    void overlayCrewIdentityOverridesCatalogName() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("displayName", "委派专业智能体");
        row.put("iconKind", "gear");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("displayName", "法务助手");
        details.put("iconKind", "agent");
        details.put("agentCode", "legal");
        details.put("subagent", true);
        QianXunServiceChatStream.overlayCrewIdentity(row, details);
        assertThat(row.get("displayName")).isEqualTo("法务助手");
        assertThat(row.get("iconKind")).isEqualTo("agent");
        assertThat(row.get("agentCode")).isEqualTo("legal");
        assertThat(row.get("subagent")).isEqualTo(true);
    }

    @Test
    void applyIncomingStatusAllowsSubagentAwaitingToRunning() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("toolName", "delegate_to_agent");
        row.put("status", "awaiting");
        QianXunServiceChatStream.applyIncomingStatus(row, "running");
        assertThat(row.get("status")).isEqualTo("running");
    }
}
