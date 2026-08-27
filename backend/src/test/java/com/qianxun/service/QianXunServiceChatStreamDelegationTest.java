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
}
