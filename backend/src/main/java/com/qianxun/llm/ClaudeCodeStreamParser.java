package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.llm.OpenAiCompatibleStreamClient.TokenUsage;
import com.qianxun.llm.OpenAiCompatibleStreamClient.ToolCallEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            String finishReason
    ) {
        static ParseResult empty() {
            return new ParseResult("", List.of(), null, "", false, "", "");
        }
    }

    private final ObjectMapper objectMapper;
    private boolean sawTextDelta;

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
        if ("stream_event".equals(type)) {
            return parseStreamEvent(n.path("event"), text(n, "session_id"));
        }
        if ("assistant".equals(type)) {
            return parseAssistant(n, text(n, "session_id"));
        }
        if ("user".equals(type)) {
            return parseUser(n, text(n, "session_id"));
        }
        if ("result".equals(type)) {
            return parseResult(n);
        }
        if ("system".equals(type)) {
            String sessionId = text(n, "session_id");
            String subtype = text(n, "subtype");
            if ("api_retry".equals(subtype)) {
                return new ParseResult("", List.of(), null, sessionId, false, "", "");
            }
            return new ParseResult("", List.of(), null, sessionId, false, "", "");
        }
        if ("error".equals(type)) {
            String err = firstNonBlank(text(n, "error"), text(n, "message"), raw);
            return new ParseResult("", List.of(), null, text(n, "session_id"), false, err, "error");
        }
        return new ParseResult("", List.of(), null, text(n, "session_id"), false, "", "");
    }

    private ParseResult parseStreamEvent(JsonNode event, String sessionId) {
        String eventType = text(event, "type");
        if ("content_block_start".equals(eventType)) {
            JsonNode block = event.path("content_block");
            if ("tool_use".equals(text(block, "type"))) {
                String id = firstNonBlank(text(block, "id"), text(block, "tool_use_id"));
                String name = firstNonBlank(text(block, "name"), "tool");
                String args = stringify(block.get("input"));
                ToolCallEvent ev = new ToolCallEvent(
                        id, name, args, null, "running", System.currentTimeMillis(), null);
                return new ParseResult("", List.of(ev), null, sessionId, false, "", "");
            }
        }
        JsonNode delta = event.path("delta");
        if (delta.isObject() && "text_delta".equals(text(delta, "type"))) {
            sawTextDelta = true;
            return new ParseResult(text(delta, "text"), List.of(), null, sessionId, false, "", "");
        }
        return new ParseResult("", List.of(), null, sessionId, false, "", "");
    }

    private ParseResult parseAssistant(JsonNode n, String sessionId) {
        JsonNode content = n.path("message").path("content");
        if (!content.isArray()) {
            content = n.path("content");
        }
        StringBuilder textOut = new StringBuilder();
        List<ToolCallEvent> tools = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode block : content) {
                String bType = text(block, "type");
                if ("text".equals(bType) && !sawTextDelta) {
                    textOut.append(text(block, "text"));
                } else if ("tool_use".equals(bType)) {
                    String id = firstNonBlank(text(block, "id"), text(block, "tool_use_id"));
                    String name = firstNonBlank(text(block, "name"), "tool");
                    String args = stringify(block.get("input"));
                    tools.add(new ToolCallEvent(id, name, args, null, "running", System.currentTimeMillis(), null));
                }
            }
        }
        return new ParseResult(textOut.toString(), List.copyOf(tools), null, sessionId, false, "", "");
    }

    private ParseResult parseUser(JsonNode n, String sessionId) {
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
                tools.add(new ToolCallEvent(
                        id, "", "", result, err ? "error" : "completed",
                        null, System.currentTimeMillis()));
            }
        }
        return new ParseResult("", List.copyOf(tools), null, sessionId, false, "", "");
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
        TokenUsage usage = usageOf(n.path("usage"), n.path("modelUsage"));
        return new ParseResult("", List.of(), usage, sessionId, true, err, finish);
    }

    private TokenUsage usageOf(JsonNode usage, JsonNode modelUsage) {
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
        return new TokenUsage(tin, tout, ttotal, null);
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
