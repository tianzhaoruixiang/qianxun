package com.qianxun.service;

import com.qianxun.domain.ChatMessage;
import com.qianxun.web.dto.ChatMessageResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CrewToolCallHydratorTest {

    @Test
    void merge_createsParentCardAndAttachesChildTools() {
        Instant t0 = Instant.parse("2026-09-01T10:00:00Z");
        List<ChatMessageResponse> parent = List.of(
                msg("u1", "s1", "user", "请法务看合同", t0, null),
                msg("a1", "s1", "assistant", "已交办法务", t0.plusMillis(1), "[]")
        );
        ChatMessage childAsst = assistant(
                "c1", "task-legal01",
                "审查意见如下",
                "[{\"toolCallId\":\"read1\",\"toolName\":\"Read\",\"status\":\"completed\"}]",
                t0.plusSeconds(2)
        );
        List<ChatMessageResponse> out = CrewToolCallHydrator.merge(parent, List.of(
                new CrewToolCallHydrator.ChildCrew(
                        "task-legal01", "legal", "法务助手", t0.plusSeconds(1), List.of(childAsst))
        ));
        List<Map<String, Object>> tools = CrewToolCallHydrator.parseTools(out.get(1).toolCallsJson());
        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).get("toolCallId")).isEqualTo("legal01");
        assertThat(tools.get(0).get("displayName")).isEqualTo("法务助手");
        assertThat(tools.get(0).get("childSessionId")).isEqualTo("task-legal01");
        assertThat(tools.get(1).get("toolCallId")).isEqualTo("read1");
        assertThat(tools.get(1).get("parentId")).isEqualTo("legal01");
        assertThat(tools.get(1).get("subagent")).isEqualTo(true);
    }

    @Test
    void merge_doesNotDuplicateExistingChildTools() {
        Instant t0 = Instant.parse("2026-09-01T10:00:00Z");
        String existing = "[{\"toolCallId\":\"legal01\",\"toolName\":\"delegate_to_agent\",\"displayName\":\"法务助手\"},"
                + "{\"toolCallId\":\"read1\",\"toolName\":\"Read\",\"parentId\":\"legal01\"}]";
        List<ChatMessageResponse> parent = List.of(
                msg("u1", "s1", "user", "问", t0, null),
                msg("a1", "s1", "assistant", "答", t0.plusMillis(1), existing)
        );
        ChatMessage childAsst = assistant(
                "c1", "task-legal01", "done",
                "[{\"toolCallId\":\"read1\",\"toolName\":\"Read\",\"status\":\"completed\"}]",
                t0.plusSeconds(2)
        );
        List<ChatMessageResponse> out = CrewToolCallHydrator.merge(parent, List.of(
                new CrewToolCallHydrator.ChildCrew(
                        "task-legal01", "legal", "法务助手", t0.plusSeconds(1), List.of(childAsst))
        ));
        assertThat(CrewToolCallHydrator.parseTools(out.get(1).toolCallsJson())).hasSize(2);
    }

    @Test
    void indexOfTargetAssistant_pairsWithTurnUser() {
        Instant t0 = Instant.parse("2026-09-01T10:00:00Z");
        Instant t1 = t0.plusSeconds(30);
        List<ChatMessageResponse> parent = List.of(
                msg("u1", "s1", "user", "第一轮", t0, null),
                msg("a1", "s1", "assistant", "答1", t0.plusMillis(1), null),
                msg("u2", "s1", "user", "第二轮", t1, null),
                msg("a2", "s1", "assistant", "答2", t1.plusMillis(1), null)
        );
        int idx = CrewToolCallHydrator.indexOfTargetAssistant(parent, t0.plusSeconds(5));
        assertThat(idx).isEqualTo(1);
        idx = CrewToolCallHydrator.indexOfTargetAssistant(parent, t1.plusSeconds(5));
        assertThat(idx).isEqualTo(3);
    }

    private static ChatMessageResponse msg(
            String id, String sessionId, String role, String content, Instant at, String tools
    ) {
        return new ChatMessageResponse(id, sessionId, role, content, tools, null, null, at, "completed", "run");
    }

    private static ChatMessage assistant(String id, String sessionId, String content, String tools, Instant at) {
        return new ChatMessage(
                id, sessionId, "assistant", content,
                null, null, null, null, tools, null, null, at, "completed", "run"
        );
    }
}
