package com.qianxun.service.stream;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentTaskRegistry {

    private final ConcurrentHashMap<String, AgentTask> byId = new ConcurrentHashMap<>();

    public void put(AgentTask task) {
        if (task != null) {
            byId.put(task.id(), task);
        }
    }

    public Optional<AgentTask> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.trim()));
    }

    public Optional<AgentTask> findActive(String parentRunId, String agentCode) {
        if (parentRunId == null || parentRunId.isBlank() || agentCode == null || agentCode.isBlank()) {
            return Optional.empty();
        }
        String code = agentCode.trim();
        return listByParentRun(parentRunId).stream()
                .filter(t -> code.equals(t.agentCode()))
                .filter(t -> !t.state().terminal())
                .min((a, b) -> Long.compare(a.createdAtMs(), b.createdAtMs()));
    }

    public List<AgentTask> listByParentRun(String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank()) {
            return List.of();
        }
        return byId.values().stream()
                .filter(t -> parentRunId.equals(t.parentRunId()))
                .sorted((a, b) -> Long.compare(b.createdAtMs(), a.createdAtMs()))
                .toList();
    }

    public List<AgentTask> listByParentSession(String parentSessionId) {
        if (parentSessionId == null || parentSessionId.isBlank()) {
            return List.of();
        }
        return byId.values().stream()
                .filter(t -> parentSessionId.equals(t.parentSessionId()))
                .sorted((a, b) -> Long.compare(b.createdAtMs(), a.createdAtMs()))
                .toList();
    }
}
