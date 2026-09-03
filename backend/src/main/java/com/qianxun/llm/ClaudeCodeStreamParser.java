package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.llm.OpenAiCompatibleStreamClient.TokenUsage;
import com.qianxun.llm.OpenAiCompatibleStreamClient.ToolCallEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析 Claude Code {@code --output-format stream-json} 的一行事件。
 */
public final class ClaudeCodeStreamParser {

    public record ParseResult(
            String token,
            List<ToolCallEvent> tools,
            TokenUsage usage,
            String sessionId,
            boolean resultDone,
            String error,
            String finishReason,
            CompactEvent compact
    ) {
        public ParseResult(
                String token,
                List<ToolCallEvent> tools,
                TokenUsage usage,
                String sessionId,
                boolean resultDone,
                String error,
                String finishReason
        ) {
            this(token, tools, usage, sessionId, resultDone, error, finishReason, null);
        }

        static ParseResult empty() {
            return new ParseResult("", List.of(), null, "", false, "", "", null);
        }
    }

    public record CompactEvent(String phase, String trigger, Integer preTokens) {
    }

    private final ObjectMapper objectMapper;
    private boolean sawTextDelta;
    private int lastLiveOut = -1;
    private int lastLiveIn = -1;

    public ClaudeCodeStreamParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParseResult accept(String line) throws Exception {
        if (line == null) {
            return ParseResult.empty();
        }
        String raw = line.trim();
        if (raw.isEmpty() || raw.charAt(0) != '{') {
            return ParseResult.empty();
        }
        JsonNode n = objectMapper.readTree(raw);
        String type = text(n, "type");
        String sessionId = text(n, "session_id");
        String parentToolUseId = parentToolUseId(n);
        boolean nested = !parentToolUseId.isBlank();
        if ("stream_event".equals(type)) {
            return parseStreamEvent(n.path("event"), sessionId, parentToolUseId);
        }
        if ("assistant".equals(type)) {
            return parseAssistant(n, sessionId, parentToolUseId);
        }
        if ("user".equals(type)) {
            return parseUser(n, sessionId, parentToolUseId);
        }
        if ("result".equals(type)) {
            if (nested) {
                return parseNestedResult(n, sessionId, parentToolUseId);
            }
            return parseResult(n);
        }
        if ("system".equals(type)) {
            String subtype = text(n, "subtype");
            if ("compact_boundary".equals(subtype)) {
                return compactFromBoundary(n, sessionId);
            }
            if ("status".equals(subtype) && "compacting".equals(text(n, "status"))) {
                return compactResult(sessionId, "start", "", null);
            }
            if ("api_retry".equals(subtype)) {
                return new ParseResult("", List.of(), null, sessionId, false, "", "");
            }
            return new ParseResult("", List.of(), null, sessionId, false, "", "");
        }
        if ("compact".equals(type)) {
            String phase = firstNonBlank(text(n, "phase"), "done");
            String trigger = text(n, "trigger");
            Integer pre = intOrNull(n, "preTokens", "pre_tokens");
            return compactResult(sessionId, phase, trigger, pre);
        }
        if ("context_usage".equals(type)) {
            return parseContextUsage(n, sessionId);
        }
        if ("heartbeat".equals(type) || "keepalive".equals(type) || "ping".equals(type)) {
            return ParseResult.empty();
        }
        if ("error".equals(type)) {
            String err = firstNonBlank(text(n, "error"), text(n, "message"), raw);
            return new ParseResult("", List.of(), null, text(n, "session_id"), false, err, "error");
        }
        return new ParseResult("", List.of(), null, text(n, "session_id"), false, "", "");
    }

    private ParseResult parseStreamEvent(JsonNode event, String sessionId, String parentToolUseId) {
        String eventType = text(event, "type");
        if ("content_block_start".equals(eventType)) {
            JsonNode block = event.path("content_block");
            if ("tool_use".equals(text(block, "type"))) {
                String id = firstNonBlank(text(block, "id"), text(block, "tool_use_id"));
                String name = firstNonBlank(text(block, "name"), "tool");
                if (isOfficerMcpTool(name) && parentToolUseId.isBlank()) {
                    return new ParseResult("", List.of(), null, sessionId, false, "", "");
                }
                String args = stringify(block.get("input"));
                return new ParseResult("", List.of(toolUse(id, name, args, parentToolUseId)),
                        null, sessionId, false, "", "");
            }
        }
        JsonNode delta = event.path("delta");
        if (delta.isObject() && "text_delta".equals(text(delta, "type"))) {
            if (!parentToolUseId.isBlank()) {
                // 子任务正文不进主气泡，避免刷屏把页面打挂
                return ParseResult.empty();
            }
            sawTextDelta = true;
            TokenUsage live = liveUsageThrottled(event.path("usage"), event.path("message").path("usage"));
            return new ParseResult(text(delta, "text"), List.of(), live, sessionId, false, "", "");
        }
        if ("message_start".equals(eventType)) {
            TokenUsage live = liveUsage(event.path("message").path("usage"), true);
            return new ParseResult("", List.of(), live, sessionId, false, "", "");
        }
        if ("message_delta".equals(eventType)) {
            TokenUsage live = liveUsageThrottled(event.path("usage"), event.path("message").path("usage"));
            return new ParseResult("", List.of(), live, sessionId, false, "", "");
        }
        return new ParseResult("", List.of(), null, sessionId, false, "", "");
    }

    private ParseResult parseAssistant(JsonNode n, String sessionId, String parentToolUseId) {
        JsonNode content = n.path("message").path("content");
        if (!content.isArray()) {
            content = n.path("content");
        }
        StringBuilder textOut = new StringBuilder();
        List<ToolCallEvent> tools = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode block : content) {
                String bType = text(block, "type");
                if ("text".equals(bType)) {
                    String t = text(block, "text");
                    if (!parentToolUseId.isBlank()) {
                        if (!t.isBlank()) {
                            tools.add(parentProgress(parentToolUseId, t));
                        }
                    } else if (!sawTextDelta) {
                        textOut.append(t);
                    }
                } else if ("tool_use".equals(bType)) {
                    String id = firstNonBlank(text(block, "id"), text(block, "tool_use_id"));
                    String name = firstNonBlank(text(block, "name"), "tool");
                    if (isOfficerMcpTool(name) && parentToolUseId.isBlank()) {
                        continue;
                    }
                    String args = stringify(block.get("input"));
                    tools.add(toolUse(id, name, args, parentToolUseId));
                }
            }
        }
        return new ParseResult(textOut.toString(), List.copyOf(tools), liveUsage(n.path("message").path("usage"), true), sessionId, false, "", "");
    }

    private ParseResult parseUser(JsonNode n, String sessionId, String parentToolUseId) {
        JsonNode content = n.path("message").path("content");
        if (!content.isArray()) {
            content = n.path("content");
        }
        List<ToolCallEvent> tools = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode block : content) {
                if (!"tool_result".equals(text(block, "type"))) {
                    continue;
                }
                String id = firstNonBlank(text(block, "tool_use_id"), text(block, "id"));
                String result = toolResultText(block);
                boolean err = block.path("is_error").asBoolean(false);
                tools.add(toolResult(id, result, err, parentToolUseId));
            }
        }
        return new ParseResult("", List.copyOf(tools), null, sessionId, false, "", "");
    }

    /** 子任务自己的 result 不是主轮结束，但应结束对应的 Agent/子智能体工具。 */
    private ParseResult parseNestedResult(JsonNode n, String sessionId, String parentToolUseId) {
        String summary = firstNonBlank(text(n, "result"), text(n, "error"));
        boolean err = "error".equals(text(n, "subtype")) || n.path("is_error").asBoolean(false);
        if (summary.isBlank() && !err) {
            return ParseResult.empty();
        }
        if (summary.isBlank()) {
            summary = "子任务失败";
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", "subagent.complete");
        details.put("summary", truncate(summary, 200));
        if (err) {
            details.put("error", truncate(summary, 500));
        }
        ToolCallEvent ev = new ToolCallEvent(
                parentToolUseId,
                "",
                "",
                truncate(summary, 500),
                err ? "error" : "completed",
                null,
                System.currentTimeMillis(),
                details);
        return new ParseResult("", List.of(ev), null, sessionId, false, "", "");
    }

    private ParseResult parseResult(JsonNode n) {
        String sessionId = text(n, "session_id");
        String subtype = text(n, "subtype");
        String err = "";
        String finish = "stop";
        if ("error".equals(subtype) || n.path("is_error").asBoolean(false)) {
            err = firstNonBlank(text(n, "error"), text(n, "result"), "Claude Code 运行失败");
            finish = "error";
        }
        TokenUsage usage = usageFromResult(n);
        return new ParseResult("", List.of(), usage, sessionId, true, err, finish);
    }

    private ParseResult parseContextUsage(JsonNode n, String sessionId) {
        Integer total = intOrNull(n, "totalTokens");
        Integer max = intOrNull(n, "maxTokens");
        Double percent = doubleOrNull(n, "percentage");
        JsonNode api = n.path("apiUsage");
        Integer cacheRead = intOrNull(api, "cache_read_input_tokens");
        Integer cacheCreate = intOrNull(api, "cache_creation_input_tokens");
        if (total == null && max == null && percent == null && cacheRead == null && cacheCreate == null) {
            return ParseResult.empty();
        }
        double pct = percent != null
                ? percent
                : (max != null && max > 0 && total != null && total > 0
                        ? total * 100.0 / max
                        : null);
        TokenUsage usage = new TokenUsage(
                null,
                null,
                null,
                max,
                total,
                pct,
                false,
                false,
                true,
                null,
                null,
                null,
                cacheRead,
                cacheCreate,
                null
        );
        return new ParseResult("", List.of(), usage, sessionId, false, "", "");
    }

    private TokenUsage usageFromResult(JsonNode n) {
        JsonNode usageNode = n.path("usage");
        JsonNode modelUsageNode = n.path("modelUsage");
        Integer in = intOrNull(usageNode, "input_tokens", "prompt_tokens");
        Integer out = intOrNull(usageNode, "output_tokens", "completion_tokens");
        Integer total = intOrNull(usageNode, "total_tokens");
        int[] tree = sumModelUsage(modelUsageNode);
        Integer treeIn = tree[0] > 0 ? tree[0] : null;
        Integer treeOut = tree[1] > 0 ? tree[1] : null;
        if (in == null && out == null && treeIn != null) {
            in = treeIn;
            out = treeOut;
            total = treeIn + (treeOut == null ? 0 : treeOut);
        }
        Integer cacheRead = firstCacheInt(usageNode, "cache_read_input_tokens");
        Integer cacheCreate = firstCacheInt(usageNode, "cache_creation_input_tokens");
        Double cost = doubleOrNull(n, "total_cost_usd");
        Long duration = longOrNull(n, "duration_ms");
        int tin = in == null ? 0 : in;
        int tout = out == null ? 0 : out;
        int ttotal = total == null ? tin + tout : total;
        if (tin <= 0 && tout <= 0 && ttotal <= 0) {
            return null;
        }
        return new TokenUsage(
                tin > 0 ? tin : null,
                tout > 0 ? tout : null,
                ttotal > 0 ? ttotal : null,
                null,
                null,
                null,
                false,
                false,
                false,
                cost,
                treeIn,
                treeOut,
                cacheRead,
                cacheCreate,
                duration
        );
    }

    private static int[] sumModelUsage(JsonNode modelUsage) {
        int sumIn = 0;
        int sumOut = 0;
        if (modelUsage == null || !modelUsage.isObject()) {
            return new int[] {0, 0};
        }
        var it = modelUsage.fields();
        while (it.hasNext()) {
            var e = it.next();
            JsonNode u = e.getValue();
            Integer i = intOrNull(u, "inputTokens", "input_tokens", "prompt_tokens");
            Integer o = intOrNull(u, "outputTokens", "output_tokens", "completion_tokens");
            if (i != null) {
                sumIn += i;
            }
            if (o != null) {
                sumOut += o;
            }
        }
        return new int[] {sumIn, sumOut};
    }

    private static Integer firstCacheInt(JsonNode usage, String field) {
        Integer direct = intOrNull(usage, field);
        if (direct != null) {
            return direct;
        }
        JsonNode details = usage.path("input_tokens_details");
        if (details.isObject()) {
            return intOrNull(details, field);
        }
        return null;
    }

    private ParseResult compactFromBoundary(JsonNode n, String sessionId) {
        JsonNode meta = n.path("compact_metadata");
        if (!meta.isObject()) {
            meta = n.path("compactMetadata");
        }
        String trigger = firstNonBlank(text(meta, "trigger"), "auto");
        Integer pre = intOrNull(meta, "pre_tokens", "preTokens");
        return compactResult(sessionId, "done", trigger, pre);
    }

    private static ParseResult compactResult(String sessionId, String phase, String trigger, Integer preTokens) {
        return new ParseResult(
                "",
                List.of(),
                null,
                sessionId,
                false,
                "",
                "",
                new CompactEvent(phase == null ? "" : phase, trigger == null ? "" : trigger, preTokens)
        );
    }

    private TokenUsage liveUsageThrottled(JsonNode primary, JsonNode fallback) {
        TokenUsage u = liveUsage(primary, false);
        if (u == null) {
            u = liveUsage(fallback, false);
        }
        if (u == null) {
            return null;
        }
        int in = u.promptTokens() == null ? 0 : u.promptTokens();
        int out = u.completionTokens() == null ? 0 : u.completionTokens();
        if (in == lastLiveIn && out - lastLiveOut < 24 && lastLiveOut >= 0) {
            return null;
        }
        lastLiveIn = in;
        lastLiveOut = out;
        return u;
    }

    private TokenUsage liveUsage(JsonNode usage, boolean force) {
        TokenUsage u = usageOf(usage, usage, true);
        if (u == null) {
            return null;
        }
        if (!force) {
            int in = u.promptTokens() == null ? 0 : u.promptTokens();
            int out = u.completionTokens() == null ? 0 : u.completionTokens();
            if (in <= 0 && out <= 0) {
                return null;
            }
        }
        return u;
    }

    private TokenUsage usageOf(JsonNode usage, JsonNode modelUsage, boolean liveOccupancy) {
        Integer in = intOrNull(usage, "input_tokens", "prompt_tokens");
        Integer out = intOrNull(usage, "output_tokens", "completion_tokens");
        Integer total = intOrNull(usage, "total_tokens");
        if (in == null && out == null && modelUsage.isObject()) {
            int sumIn = 0;
            int sumOut = 0;
            var it = modelUsage.fields();
            boolean any = false;
            while (it.hasNext()) {
                var e = it.next();
                JsonNode u = e.getValue();
                Integer i = intOrNull(u, "input_tokens", "prompt_tokens");
                Integer o = intOrNull(u, "output_tokens", "completion_tokens");
                if (i != null) {
                    sumIn += i;
                    any = true;
                }
                if (o != null) {
                    sumOut += o;
                    any = true;
                }
            }
            if (any) {
                in = sumIn;
                out = sumOut;
            }
        }
        if (in == null && out == null && total == null) {
            return null;
        }
        int tin = in == null ? 0 : in;
        int tout = out == null ? 0 : out;
        int ttotal = total == null ? tin + tout : total;
        if (in == null && out == null && total == null) {
            return null;
        }
        // live / 中间帧只透传计费字段，contextUsed 由 context_usage 事件单独提供
        return new TokenUsage(tin, tout, ttotal, null, null, null, false, liveOccupancy, false,
                null, null, null, null, null, null);
    }

    private static String parentToolUseId(JsonNode n) {
        String id = firstNonBlank(text(n, "parent_tool_use_id"), text(n, "parentToolUseId"));
        if (!id.isBlank()) {
            return id;
        }
        JsonNode event = n.get("event");
        if (event != null && event.isObject()) {
            return firstNonBlank(text(event, "parent_tool_use_id"), text(event, "parentToolUseId"));
        }
        return "";
    }

    /**
     * 干警 MCP 委派由 {@code AgentTaskService} 投影专业智能体卡片；
     * 若再把 SDK 的 tool_use 当成子智能体，前端会多出一张「墨川小助手」。
     */
    static boolean isOfficerMcpTool(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.contains("qianxun-officer")) {
            return true;
        }
        return "delegate_to_agent".equals(n)
                || "get_agent_task".equals(n)
                || "cancel_agent_task".equals(n);
    }

    private ToolCallEvent toolUse(String id, String name, String args, String parentToolUseId) {
        if (parentToolUseId.isBlank()) {
            return new ToolCallEvent(id, name, args, null, "running", System.currentTimeMillis(), null);
        }
        LinkedHashMap<String, Object> details = subagentDetails(parentToolUseId, name, "subagent.tool");
        details.put("summary", truncate("正在调用 " + name, 200));
        return new ToolCallEvent(
                id, name, args, null, "running", System.currentTimeMillis(), null, details);
    }

    private ToolCallEvent toolResult(String id, String result, boolean err, String parentToolUseId) {
        Map<String, Object> details = parentToolUseId.isBlank()
                ? null
                : subagentDetails(parentToolUseId, "", "subagent.tool");
        return new ToolCallEvent(
                id, "", "", result, err ? "error" : "completed",
                null, System.currentTimeMillis(), details);
    }

    private ToolCallEvent parentProgress(String parentToolUseId, String text) {
        String clipped = truncate(text, 500);
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", "subagent.progress");
        details.put("progress", clipped);
        details.put("summary", truncate(text, 200));
        return new ToolCallEvent(
                parentToolUseId, "", "", clipped, "running", null, null, details);
    }

    private static LinkedHashMap<String, Object> subagentDetails(
            String parentToolUseId, String childToolName, String eventType) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("subagent", true);
        details.put("parentId", parentToolUseId);
        details.put("eventType", eventType);
        if (childToolName != null && !childToolName.isBlank()) {
            details.put("childToolName", childToolName);
        }
        return details;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private String stringify(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return "{}";
        }
        if (n.isTextual()) {
            return n.asText();
        }
        try {
            return objectMapper.writeValueAsString(n);
        } catch (Exception ex) {
            return n.toString();
        }
    }

    private static String toolResultText(JsonNode block) {
        JsonNode content = block.get("content");
        if (content == null || content.isNull() || content.isMissingNode()) {
            return text(block, "result");
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode c : content) {
                if ("text".equals(text(c, "type"))) {
                    sb.append(text(c, "text"));
                } else if (c.isTextual()) {
                    sb.append(c.asText());
                }
            }
            return sb.toString();
        }
        return content.toString();
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.isObject()) {
            return "";
        }
        JsonNode v = n.get(field);
        return v == null || !v.isTextual() ? "" : v.asText("");
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) {
            return "";
        }
        for (String x : xs) {
            if (x != null && !x.isBlank()) {
                return x;
            }
        }
        return "";
    }

    private static Double doubleOrNull(JsonNode n, String field) {
        if (n == null || !n.isObject()) {
            return null;
        }
        JsonNode v = n.get(field);
        if (v != null && v.isNumber()) {
            return v.doubleValue();
        }
        return null;
    }

    private static Long longOrNull(JsonNode n, String field) {
        if (n == null || !n.isObject()) {
            return null;
        }
        JsonNode v = n.get(field);
        if (v != null && v.isNumber()) {
            return v.longValue();
        }
        return null;
    }

    private static Integer intOrNull(JsonNode n, String... fields) {
        if (n == null || !n.isObject()) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && v.isNumber()) {
                return v.asInt();
            }
        }
        return null;
    }

    public static Map<String, Object> details(String rawLine) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (rawLine != null && !rawLine.isBlank()) {
            m.put("raw", rawLine.length() > 2000 ? rawLine.substring(0, 2000) : rawLine);
        }
        return m;
    }
}
