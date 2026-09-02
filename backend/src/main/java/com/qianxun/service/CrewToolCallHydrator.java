package com.qianxun.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.domain.ChatMessage;
import com.qianxun.service.stream.AgentTask;
import com.qianxun.web.dto.ChatMessageResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 切回历史会话时，把 {@code task-*} 子会话里的工具时间线挂回父助手消息。
 * 直播路径会把委派投影写入父消息；旧数据或父轮先结束时靠这里回填。
 */
final class CrewToolCallHydrator {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP =
            new TypeReference<>() {};

    private CrewToolCallHydrator() {}

    record ChildCrew(
            String sessionId,
            String agentCode,
            String agentName,
            Instant createdAt,
            List<ChatMessage> messages
    ) {}

    static List<ChatMessageResponse> merge(
            List<ChatMessageResponse> parentMessages,
            List<ChildCrew> children
    ) {
        if (parentMessages == null || parentMessages.isEmpty()
                || children == null || children.isEmpty()) {
            return parentMessages;
        }
        List<ChatMessageResponse> out = new ArrayList<>(parentMessages);
        for (ChildCrew child : children) {
            if (child == null || child.sessionId() == null || child.sessionId().isBlank()) {
                continue;
            }
            String taskId = AgentTask.taskIdFromSession(child.sessionId());
            if (taskId.isBlank()) {
                continue;
            }
            int idx = indexOfTargetAssistant(out, child.createdAt());
            if (idx < 0) {
                continue;
            }
            ChatMessageResponse assistant = out.get(idx);
            List<Map<String, Object>> tools = parseTools(assistant.toolCallsJson());
            mergeChildInto(tools, child, taskId);
            out.set(idx, withToolCalls(assistant, writeTools(tools)));
        }
        return List.copyOf(out);
    }

    static void mergeChildInto(List<Map<String, Object>> tools, ChildCrew child, String taskId) {
        ChatMessage childAssistant = lastAssistant(child.messages());
        List<Map<String, Object>> childTools = childAssistant == null
                ? List.of()
                : parseTools(childAssistant.toolCallsJson());
        String artifact = childAssistant == null || childAssistant.content() == null
                ? ""
                : childAssistant.content();

        Map<String, Object> card = findById(tools, taskId);
        if (card == null) {
            card = syntheticCard(child, taskId, artifact);
            tools.add(card);
        } else {
            overlayCardIdentity(card, child, taskId);
            if (blank(card.get("result")) && !artifact.isBlank()) {
                card.put("result", artifact);
            }
            if (blank(card.get("status"))) {
                card.put("status", "completed");
            }
        }

        java.util.Set<String> childIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : childTools) {
            String id = text(row.get("toolCallId"));
            if (!id.isBlank()) {
                childIds.add(id);
            }
        }
        for (Map<String, Object> row : childTools) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>(row);
            String id = text(copy.get("toolCallId"));
            if (id.isBlank() || id.equals(taskId)) {
                continue;
            }
            String pid = text(copy.get("parentId"));
            if (pid.isBlank() || !childIds.contains(pid)) {
                copy.put("parentId", taskId);
            }
            copy.put("subagent", true);
            Map<String, Object> existing = findById(tools, id);
            if (existing == null) {
                tools.add(copy);
            } else if (blank(existing.get("parentId"))) {
                existing.put("parentId", copy.get("parentId"));
                existing.putIfAbsent("subagent", true);
            }
        }
    }

    static int indexOfTargetAssistant(List<ChatMessageResponse> messages, Instant childCreatedAt) {
        int userIdx = -1;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessageResponse m = messages.get(i);
            if (!"user".equals(m.role())) {
                continue;
            }
            if (childCreatedAt != null && m.createdAt() != null && m.createdAt().isAfter(childCreatedAt)) {
                break;
            }
            userIdx = i;
        }
        if (userIdx >= 0) {
            for (int i = userIdx + 1; i < messages.size(); i++) {
                if ("assistant".equals(messages.get(i).role())) {
                    return i;
                }
            }
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).role())) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Object> syntheticCard(ChildCrew child, String taskId, String artifact) {
        LinkedHashMap<String, Object> card = new LinkedHashMap<>();
        overlayCardIdentity(card, child, taskId);
        card.put("toolName", AgentTaskService.TOOL_NAME);
        card.put("iconKind", "agent");
        card.put("subagent", true);
        card.put("status", "completed");
        if (child.createdAt() != null) {
            card.put("startedAt", child.createdAt().toEpochMilli());
        }
        if (!artifact.isBlank()) {
            card.put("result", artifact);
            card.put("summary", artifact.length() > 200 ? artifact.substring(0, 200) : artifact);
        }
        String code = child.agentCode() == null ? "" : child.agentCode().trim();
        String msg = "{\"agentCode\":\"" + escapeJson(code) + "\"}";
        card.put("args", msg);
        return card;
    }

    private static void overlayCardIdentity(Map<String, Object> card, ChildCrew child, String taskId) {
        card.put("toolCallId", taskId);
        card.put("delegationId", taskId);
        card.put("childSessionId", child.sessionId());
        if (child.agentCode() != null && !child.agentCode().isBlank()) {
            card.put("agentCode", child.agentCode());
        }
        String name = child.agentName() == null || child.agentName().isBlank()
                ? "专业智能体"
                : child.agentName().trim();
        card.put("displayName", name);
        card.put("iconKind", "agent");
        card.put("subagent", true);
        card.putIfAbsent("toolName", AgentTaskService.TOOL_NAME);
    }

    private static ChatMessage lastAssistant(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m != null && "assistant".equals(m.role())) {
                return m;
            }
        }
        return null;
    }

    private static Map<String, Object> findById(List<Map<String, Object>> tools, String id) {
        for (Map<String, Object> row : tools) {
            if (id.equals(text(row.get("toolCallId")))) {
                return row;
            }
        }
        return null;
    }

    static List<Map<String, Object>> parseTools(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode root = JSON.readTree(json);
            if (root == null || !root.isArray()) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode n : root) {
                if (n != null && n.isObject()) {
                    out.add(JSON.convertValue(n, MAP));
                }
            }
            return out;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    static String writeTools(List<Map<String, Object>> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(tools);
        } catch (Exception ex) {
            return null;
        }
    }

    private static ChatMessageResponse withToolCalls(ChatMessageResponse m, String toolCallsJson) {
        return new ChatMessageResponse(
                m.id(), m.sessionId(), m.role(), m.content(),
                toolCallsJson, m.usageJson(), m.suggestionsJson(),
                m.createdAt(), m.status(), m.runId()
        );
    }

    private static boolean blank(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    private static String text(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
