package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dashboard {@code /api/ws} 的 JSON-RPC 2.0 帧（与 tui_gateway 一致）：
 * 请求 {@code {jsonrpc,id,method,params}}，事件 {@code method=event}。
 *
 * <p>工具相关事件（v2026.8.3 tui_gateway）：
 * <ul>
 *   <li>{@code tool.start} — tool_id, name, context；verbose 时另有 args_text</li>
 *   <li>{@code tool.complete} — tool_id, name, args, result, duration_s；可选 summary /
 *       result_text(verbose)、todos、inline_diff</li>
 *   <li>{@code tool.generating} — 仅 name（无 tool_id）</li>
 *   <li>{@code tool.output_risk} — tool_id, name, risk, findings, redacted</li>
 * </ul>
 * 无独立的 {@code tool.error} / {@code tool.end} / 流式 stdout；结束以 {@code tool.complete} 为准。
 *
 * <p>子智能体事件（v2026.8+ tui_gateway，挂在父会话 sid）：
 * <ul>
 *   <li>{@code subagent.start} / {@code subagent.spawn_requested}</li>
 *   <li>{@code subagent.tool} / {@code subagent.thinking} / {@code subagent.progress}</li>
 *   <li>{@code subagent.complete}</li>
 * </ul>
 * {@code subagent.text} 仅用于子会话镜像，父会话不转发。
 */
public final class HermesDashboardRpc {

    private HermesDashboardRpc() {}

    public static String request(ObjectMapper mapper, String id, String method, Map<String, Object> params)
            throws Exception {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.put("params", params == null ? Map.of() : params);
        return mapper.writeValueAsString(body);
    }

    public static String eventType(JsonNode root) {
        if (root == null || !"event".equals(root.path("method").asText(""))) {
            return "";
        }
        return root.path("params").path("type").asText("");
    }

    public static String eventSessionId(JsonNode root) {
        if (root == null) {
            return "";
        }
        return root.path("params").path("session_id").asText("");
    }

    public static JsonNode eventPayload(JsonNode root) {
        if (root == null) {
            return null;
        }
        return root.path("params").path("payload");
    }

    public static boolean isRpcResult(JsonNode root) {
        return root != null && root.hasNonNull("id") && !"event".equals(root.path("method").asText(""));
    }

    public static String rpcId(JsonNode root) {
        if (root == null || root.path("id").isMissingNode() || root.path("id").isNull()) {
            return "";
        }
        return root.path("id").asText();
    }

    public static String rpcError(JsonNode root) {
        if (root == null) {
            return "";
        }
        JsonNode err = root.path("error");
        if (err.isMissingNode() || err.isNull()) {
            return "";
        }
        String msg = err.path("message").asText("");
        if (!msg.isBlank()) {
            return msg;
        }
        return err.toString();
    }

    public static OpenAiCompatibleStreamClient.ToolCallEvent toToolEvent(String type, JsonNode payload) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("tool.")) {
            return null;
        }
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            if ("tool.generating".equals(normalized)) {
                return null;
            }
            return null;
        }

        String id = firstText(payload, "tool_id", "toolCallId", "id");
        String name = firstText(payload, "name", "tool", "function", "tool_name");
        if (id.isBlank() && name.isBlank()) {
            return null;
        }
        if (id.isBlank()) {
            id = "call_" + (name.isBlank() ? "tool" : name);
        }

        long now = System.currentTimeMillis();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", type.trim());

        String context = firstText(payload, "context", "preview");
        if (!context.isBlank()) {
            details.put("context", context);
        }
        String summary = firstText(payload, "summary");
        if (!summary.isBlank()) {
            details.put("summary", summary);
        }
        Double durationS = firstDouble(payload, "duration_s", "durationSeconds", "duration");
        if (durationS != null) {
            details.put("durationSeconds", durationS);
        }
        String inlineDiff = firstText(payload, "inline_diff", "inlineDiff");
        if (!inlineDiff.isBlank()) {
            details.put("inlineDiff", inlineDiff);
        }
        JsonNode todos = payload.get("todos");
        if (todos != null && !todos.isMissingNode() && !todos.isNull()) {
            details.put("todos", nodeToJava(todos));
        }
        String risk = firstText(payload, "risk");
        if (!risk.isBlank()) {
            details.put("risk", risk);
        }
        JsonNode findings = payload.get("findings");
        if (findings != null && findings.isArray()) {
            List<String> list = new ArrayList<>();
            for (JsonNode n : findings) {
                if (n != null && n.isTextual() && !n.asText("").isBlank()) {
                    list.add(n.asText().trim());
                }
            }
            if (!list.isEmpty()) {
                details.put("findings", list);
            }
        }
        if (payload.has("redacted") && payload.path("redacted").isBoolean()) {
            details.put("redacted", payload.path("redacted").asBoolean());
        }
        String err = firstText(payload, "error", "error_message", "message");
        if (!err.isBlank() && ("tool.error".equals(normalized) || payload.has("error"))) {
            details.put("error", err);
        }

        String args = extractArgs(payload);
        String result = extractResult(payload);
        String resultText = firstText(payload, "result_text", "resultText", "output", "stdout");
        if (!resultText.isBlank()) {
            details.put("resultText", resultText);
            if (result == null || result.isBlank()) {
                result = resultText;
            }
        }
        String stderr = firstText(payload, "stderr");
        if (!stderr.isBlank()) {
            details.put("stderr", stderr);
        }

        if ("tool.start".equals(normalized) || "tool.started".equals(normalized)) {
            return new OpenAiCompatibleStreamClient.ToolCallEvent(
                    id, name, args, null, "running", now, null, details);
        }
        if ("tool.generating".equals(normalized)) {
            return new OpenAiCompatibleStreamClient.ToolCallEvent(
                    id, name, args, null, "running", now, null, details);
        }
        if ("tool.output_risk".equals(normalized)) {
            return new OpenAiCompatibleStreamClient.ToolCallEvent(
                    id, name, args, null, "running", null, null, details);
        }
        if ("tool.complete".equals(normalized)
                || "tool.completed".equals(normalized)
                || "tool.end".equals(normalized)
                || "tool.result".equals(normalized)) {
            Long endedAt = now;
            Long startedAt = null;
            if (durationS != null && durationS >= 0) {
                startedAt = now - Math.round(durationS * 1000.0);
            }
            String status = "completed";
            if (!err.isBlank() && payload.has("error")) {
                status = "error";
            } else {
                String failure = resultFailureMessage(result);
                if (failure == null) {
                    failure = resultFailureMessage(resultText);
                }
                if (failure != null) {
                    status = "error";
                    if (!details.containsKey("error")) {
                        details.put("error", truncate(failure, 500));
                    }
                }
            }
            return new OpenAiCompatibleStreamClient.ToolCallEvent(
                    id, name, args, result, status, startedAt, endedAt, details);
        }
        if ("tool.error".equals(normalized) || "tool.failed".equals(normalized)) {
            if (result == null || result.isBlank()) {
                result = err;
            }
            if (!details.containsKey("error") && err.isBlank() && result != null) {
                details.put("error", truncate(result, 500));
            }
            return new OpenAiCompatibleStreamClient.ToolCallEvent(
                    id, name, args, result, "error", null, now, details);
        }
        if ("tool.progress".equals(normalized) || "tool.output".equals(normalized)) {
            String progress = firstText(payload, "text", "preview", "chunk", "output");
            if (progress.isBlank()) {
                progress = resultText;
            }
            if (!progress.isBlank()) {
                details.put("progress", progress);
            }
            return new OpenAiCompatibleStreamClient.ToolCallEvent(
                    id, name, args, progress.isBlank() ? null : progress, "running", null, null, details);
        }
        // 未知 tool.*：尽量透传，状态视为 running
        return new OpenAiCompatibleStreamClient.ToolCallEvent(
                id, name, args, result, "running", null, null, details);
    }

    /**
     * 将 Dashboard {@code subagent.*} 转为可 SSE 转发的工具事件。
     * 不处理 {@code subagent.text}（父会话无意义）。
     */
    public static OpenAiCompatibleStreamClient.ToolCallEvent toSubagentEvent(String type, JsonNode payload) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("subagent.")) {
            return null;
        }
        if ("subagent.text".equals(normalized)) {
            return null;
        }
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return null;
        }

        String subId = firstText(payload, "subagent_id", "child_session_id");
        Integer taskIndex = firstInt(payload, "task_index");
        if (subId.isBlank()) {
            if (taskIndex != null) {
                subId = "subagent-" + taskIndex;
            } else {
                subId = "subagent-" + Integer.toHexString(System.identityHashCode(payload));
            }
        }

        long now = System.currentTimeMillis();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", type.trim());
        details.put("subagent", true);

        String goal = firstText(payload, "goal");
        if (!goal.isBlank()) {
            details.put("context", truncate(goal, 200));
        }
        if (taskIndex != null) {
            details.put("taskIndex", taskIndex);
        }
        Integer taskCount = firstInt(payload, "task_count");
        if (taskCount != null) {
            details.put("taskCount", taskCount);
        }
        String parentId = firstText(payload, "parent_id");
        if (!parentId.isBlank()) {
            details.put("parentId", parentId);
        }
        String childSid = firstText(payload, "child_session_id");
        if (!childSid.isBlank()) {
            details.put("childSessionId", childSid);
        }
        String model = firstText(payload, "model");
        if (!model.isBlank()) {
            details.put("model", model);
        }
        String toolName = firstText(payload, "tool_name", "toolName", "name");
        if (!toolName.isBlank()) {
            details.put("childToolName", toolName);
        }
        String summary = firstText(payload, "summary");
        if (!summary.isBlank()) {
            details.put("summary", truncate(summary, 400));
        }
        Double durationS = firstDouble(payload, "duration_seconds", "durationSeconds", "duration_s");
        if (durationS != null) {
            details.put("durationSeconds", durationS);
        }
        Integer apiCalls = firstInt(payload, "api_calls", "apiCalls");
        if (apiCalls != null) {
            details.put("apiCalls", apiCalls);
        }
        Integer toolCount = firstInt(payload, "tool_count", "toolCount");
        if (toolCount != null) {
            details.put("toolCount", toolCount);
        }

        LinkedHashMap<String, Object> argsMap = new LinkedHashMap<>();
        if (!goal.isBlank()) {
            argsMap.put("goal", goal);
        }
        if (taskIndex != null) {
            argsMap.put("task_index", taskIndex);
        }
        if (taskCount != null) {
            argsMap.put("task_count", taskCount);
        }
        if (!toolName.isBlank()) {
            argsMap.put("tool", toolName);
        }
        String argsJson = argsMap.isEmpty() ? "" : writeQuietJson(argsMap);

        String text = firstText(payload, "text", "tool_preview", "preview");
        if (!text.isBlank()) {
            details.put("progress", truncate(text, 500));
        }

        String upstreamStatus = firstText(payload, "status").toLowerCase(Locale.ROOT);
        boolean terminal = "subagent.complete".equals(normalized);
        String status = "running";
        String result = null;
        Long endedAt = null;
        Long startedAt = null;
        if (terminal) {
            endedAt = now;
            if (durationS != null && durationS >= 0) {
                startedAt = now - Math.round(durationS * 1000.0);
            }
            boolean failed = FAILED_STATUS_VALUES.contains(upstreamStatus);
            if (!summary.isBlank()) {
                result = summary;
            } else if (!text.isBlank()) {
                result = text;
            } else if (failed && !upstreamStatus.isBlank()) {
                result = upstreamStatus;
            }
            String failure = failed ? null : resultFailureMessage(result);
            if (failure != null) {
                failed = true;
            }
            status = failed ? "error" : "completed";
            if (failed && !details.containsKey("error")) {
                String reason = failure != null ? failure : (result == null ? upstreamStatus : result);
                details.put("error", truncate(reason, 500));
            }
        } else if ("subagent.start".equals(normalized) || "subagent.spawn_requested".equals(normalized)) {
            startedAt = now;
            if (!text.isBlank()) {
                details.put("summary", truncate(
                        goal.isBlank() ? ("子智能体已启动：" + text) : ("子智能体已启动：" + goal), 200));
            } else if (!goal.isBlank()) {
                details.put("summary", truncate("子智能体已启动：" + goal, 200));
            }
        } else if ("subagent.tool".equals(normalized)) {
            String label = toolName.isBlank() ? "工具" : ClaudeCodeToolCatalog.fallbackDisplayName(toolName);
            String preview;
            if (text.isBlank() || looksLikeJsonText(text)) {
                preview = label;
            } else {
                preview = label + " · " + text;
            }
            details.put("summary", truncate("正在调用 " + preview, 200));
            result = text.isBlank() ? null : text;
        } else if ("subagent.thinking".equals(normalized) || "subagent.progress".equals(normalized)) {
            if (!text.isBlank()) {
                details.put("summary", truncate(text, 200));
                result = text;
            }
        }

        return new OpenAiCompatibleStreamClient.ToolCallEvent(
                subId, "subagent", argsJson, result, status, startedAt, endedAt, details);
    }

    /**
     * {@code subagent.tool} 除刷新父卡片外，再发一条挂在子智能体 id 下的真实工具行，
     * 否则前端按 parentId 找不到子调用。
     */
    public static OpenAiCompatibleStreamClient.ToolCallEvent toSubagentChildToolEvent(
            String type, JsonNode payload) {
        if (type == null || type.isBlank() || payload == null || payload.isMissingNode() || payload.isNull()) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("subagent.tool")) {
            return null;
        }
        String parentId = firstText(payload, "subagent_id", "child_session_id");
        Integer taskIndex = firstInt(payload, "task_index");
        if (parentId.isBlank()) {
            parentId = taskIndex != null ? "subagent-" + taskIndex : "";
        }
        if (parentId.isBlank()) {
            return null;
        }
        String toolName = firstText(payload, "tool_name", "toolName", "name");
        if (toolName.isBlank()) {
            toolName = "unknown_tool";
        }
        String explicitId = firstText(payload, "tool_call_id", "toolCallId", "tool_use_id", "tool_id", "call_id");
        String text = firstText(payload, "text", "tool_preview", "preview");
        String childId = explicitId;
        if (childId.isBlank()) {
            childId = parentId + ":" + toolName + ":" + Integer.toUnsignedString(Objects.hash(text, taskIndex));
        }
        long now = System.currentTimeMillis();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", type.trim());
        details.put("parentId", parentId);
        String context = firstText(payload, "goal");
        if (!context.isBlank()) {
            details.put("context", truncate(context, 200));
        }
        String label = ClaudeCodeToolCatalog.fallbackDisplayName(toolName);
        String preview;
        if (text.isBlank() || looksLikeJsonText(text)) {
            preview = label;
        } else {
            preview = label + " · " + text;
        }
        details.put("summary", truncate("正在调用 " + preview, 200));

        String upstreamStatus = firstText(payload, "status").toLowerCase(Locale.ROOT);
        boolean failed = FAILED_STATUS_VALUES.contains(upstreamStatus);
        boolean terminal = failed
                || "subagent.tool.complete".equals(normalized)
                || "subagent.tool_complete".equals(normalized)
                || COMPLETED_STATUS_VALUES.contains(upstreamStatus);
        String status = terminal ? (failed ? "error" : "completed") : "running";
        Long endedAt = terminal ? now : null;
        String result = text.isBlank() ? null : text;
        LinkedHashMap<String, Object> argsMap = new LinkedHashMap<>();
        argsMap.put("tool", toolName);
        if (!text.isBlank()) {
            argsMap.put("preview", truncate(text, 400));
        }
        return new OpenAiCompatibleStreamClient.ToolCallEvent(
                childId,
                toolName,
                writeQuietJson(argsMap),
                result,
                status,
                terminal ? null : now,
                endedAt,
                details);
    }

    /**
     * 后台 {@code delegate_task} 派工完成后，把「已完成」改写为「等待子智能体」状态，
     * 避免前端过早显示成功。
     */
    public static OpenAiCompatibleStreamClient.ToolCallEvent asAwaitingBackground(
            OpenAiCompatibleStreamClient.ToolCallEvent tc,
            JsonNode payload
    ) {
        if (tc == null) {
            return null;
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        if (tc.details() != null) {
            details.putAll(tc.details());
        }
        details.put("awaitingBackground", true);
        details.putIfAbsent("eventType", "tool.complete");

        String summary = buildDispatchedSummary(payload, tc.result());
        if (!summary.isBlank()) {
            details.put("summary", summary);
            details.put("progress", summary);
        }

        return new OpenAiCompatibleStreamClient.ToolCallEvent(
                tc.toolCallId(),
                tc.functionName(),
                tc.argsChunk(),
                tc.result(),
                "awaiting",
                tc.startedAt() != null ? tc.startedAt() : System.currentTimeMillis(),
                null,
                details
        );
    }

    public static OpenAiCompatibleStreamClient.ToolCallEvent awaitingProgress(
            String toolCallId,
            String progressText
    ) {
        if (toolCallId == null || toolCallId.isBlank() || progressText == null || progressText.isBlank()) {
            return null;
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("awaitingBackground", true);
        details.put("progress", truncate(progressText, 500));
        details.put("summary", truncate(progressText, 200));
        details.put("eventType", "tool.progress");
        return new OpenAiCompatibleStreamClient.ToolCallEvent(
                toolCallId,
                "delegate_task",
                null,
                progressText,
                "awaiting",
                null,
                null,
                details
        );
    }

    public static OpenAiCompatibleStreamClient.ToolCallEvent completeAwaiting(
            String toolCallId,
            String summary
    ) {
        if (toolCallId == null || toolCallId.isBlank()) {
            return null;
        }
        long now = System.currentTimeMillis();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("awaitingBackground", false);
        details.put("eventType", "tool.complete");
        if (summary != null && !summary.isBlank()) {
            details.put("summary", truncate(summary, 200));
        }
        return new OpenAiCompatibleStreamClient.ToolCallEvent(
                toolCallId,
                "delegate_task",
                null,
                summary,
                "completed",
                null,
                now,
                details
        );
    }

    private static String buildDispatchedSummary(JsonNode payload, String resultFallback) {
        JsonNode result = payload == null ? null : payload.path("result");
        int count = 0;
        if (result != null && result.isObject()) {
            if (result.path("count").isNumber()) {
                count = result.path("count").asInt();
            } else if (result.path("goals").isArray()) {
                count = result.path("goals").size();
            }
        }
        if (count <= 0 && resultFallback != null) {
            try {
                // 粗略从 JSON 文本取 count
                int idx = resultFallback.indexOf("\"count\"");
                if (idx >= 0) {
                    String tail = resultFallback.substring(idx);
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("\"count\"\\s*:\\s*(\\d+)").matcher(tail);
                    if (m.find()) {
                        count = Integer.parseInt(m.group(1));
                    }
                }
            } catch (Exception ignored) {
                /* ignore */
            }
        }
        if (count > 1) {
            return "已派工 " + count + " 个子智能体，后台并行执行中…";
        }
        if (count == 1) {
            return "已派工子智能体，后台执行中…";
        }
        return "已派工子智能体，后台执行中…";
    }

    private static String writeQuietJson(Map<String, Object> map) {
        try {
            return new ObjectMapper().writeValueAsString(map);
        } catch (Exception ex) {
            return map.toString();
        }
    }

    /**
     * Dashboard {@code _get_usage}：{@code input}/{@code output}/{@code total} 是会话累计；
     * {@code context_used}/{@code context_max}/{@code context_percent} 是当前窗口占用。
     */
    public static OpenAiCompatibleStreamClient.TokenUsage toUsage(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull() || !usage.isObject()) {
            return null;
        }
        Integer prompt = firstInt(usage, "input", "prompt", "prompt_tokens");
        Integer completion = firstInt(usage, "output", "completion", "completion_tokens");
        Integer total = firstInt(usage, "total", "total_tokens");
        Integer window = firstInt(usage, "context_max", "context_window");
        Integer contextUsed = firstInt(usage, "context_used");
        Double contextPercent = firstDouble(usage, "context_percent");
        if (prompt == null && completion == null && total == null
                && window == null && contextUsed == null && contextPercent == null) {
            return null;
        }
        return new OpenAiCompatibleStreamClient.TokenUsage(
                prompt, completion, total, window, contextUsed, contextPercent, true, false);
    }

    public static String deltaText(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return "";
        }
        if (payload.isTextual()) {
            return payload.asText("");
        }
        return payload.path("text").asText("");
    }

    private static String extractArgs(JsonNode payload) {
        String argsText = firstText(payload, "args_text", "argsText");
        JsonNode argsNode = payload.get("args");
        if (argsNode != null && !argsNode.isMissingNode() && !argsNode.isNull()) {
            if (argsNode.isTextual()) {
                String t = argsNode.asText("").trim();
                return t.isBlank() ? argsText : t;
            }
            if (argsNode.isObject() || argsNode.isArray()) {
                return argsNode.toString();
            }
        }
        if (!argsText.isBlank()) {
            return argsText;
        }
        // start 往往只有 context；先当作调用摘要，complete 再覆盖完整 args
        return firstText(payload, "context");
    }

    private static String extractResult(JsonNode payload) {
        JsonNode r = payload.get("result");
        if (r != null && !r.isMissingNode() && !r.isNull()) {
            return r.isTextual() ? r.asText() : r.toString();
        }
        return "";
    }

    static boolean looksLikeJsonText(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return (t.startsWith("{") && t.endsWith("}"))
                || (t.startsWith("[") && t.endsWith("]"));
    }

    private static boolean looksLikeErrorResult(String result) {
        if (result == null || result.isBlank()) {
            return false;
        }
        String t = result.trim();
        if (t.length() > 400) {
            t = t.substring(0, 400);
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return lower.startsWith("error:")
                || lower.startsWith("traceback (most recent call last)");
    }

    private static final Set<String> FAILED_STATUS_VALUES = Set.of(
            "error", "failed", "failure", "timeout", "timed_out",
            "cancelled", "canceled", "interrupted", "aborted", "denied", "rejected");

    private static final Set<String> COMPLETED_STATUS_VALUES = Set.of(
            "complete", "completed", "success", "succeeded", "done", "ok", "finished");

    private static final ObjectMapper RESULT_PROBE_MAPPER = new ObjectMapper();

    /**
     * 工具结果是否表示失败；失败时返回可展示的原因，成功返回 {@code null}。
     * Hermes 多数工具即使失败也走 {@code tool.complete}，只在结果体里标 success/ok/exit_code，
     * 因此不能只看纯文本前缀。
     */
    static String resultFailureMessage(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        String t = result.trim();
        if (looksLikeJsonText(t)) {
            try {
                return structuredFailureMessage(RESULT_PROBE_MAPPER.readTree(t));
            } catch (Exception ignored) {
                return null;
            }
        }
        return looksLikeErrorResult(t) ? t : null;
    }

    private static String structuredFailureMessage(JsonNode result) {
        if (result == null || !result.isObject()) {
            return null;
        }
        boolean failed = false;

        JsonNode ok = result.get("ok");
        if (ok != null && ok.isBoolean() && !ok.asBoolean()) {
            failed = true;
        }
        JsonNode success = result.get("success");
        if (success != null && success.isBoolean() && !success.asBoolean()) {
            failed = true;
        }
        String status = firstText(result, "status", "state").toLowerCase(Locale.ROOT);
        if (FAILED_STATUS_VALUES.contains(status)) {
            failed = true;
        }
        Integer exit = firstInt(result, "exit_code", "exitCode", "returncode");
        if (exit != null && exit != 0) {
            failed = true;
        }
        if (result.path("blocked_by_policy").asBoolean(false)) {
            failed = true;
        }
        String err = firstText(result, "error", "error_message", "errorMessage");
        if (!err.isBlank()) {
            failed = true;
        }

        if (!failed) {
            return null;
        }
        if (err.isBlank()) {
            err = firstText(result, "message", "detail", "reason", "stderr");
        }
        if (err.isBlank() && exit != null && exit != 0) {
            err = "命令退出码 " + exit;
        }
        return err.isBlank() ? "工具执行失败" : err;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    private static Object nodeToJava(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isIntegralNumber()) {
            return n.asLong();
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        if (n.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode c : n) {
                list.add(nodeToJava(c));
            }
            return list;
        }
        if (n.isObject()) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            n.fields().forEachRemaining(e -> map.put(e.getKey(), nodeToJava(e.getValue())));
            return map;
        }
        return n.toString();
    }

    private static String firstText(JsonNode n, String... keys) {
        for (String k : keys) {
            String v = n.path(k).asText("");
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static Integer firstInt(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (v.isNumber()) {
                return v.asInt();
            }
        }
        return null;
    }

    private static Double firstDouble(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (v.isNumber()) {
                return v.asDouble();
            }
        }
        return null;
    }
}
