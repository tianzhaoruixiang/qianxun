package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleStreamClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleStreamClient.class);

    /** 流式结束元数据：是否收到 [DONE]、最后一帧的 finish_reason（如 length/stop） */
    public record StreamCompletionMeta(boolean sawDone, String finishReason) {}

    /** 工具调用累积器（函数名 + 参数片段） */
    private record ToolCallAcc(StringBuilder name, StringBuilder args) {}

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper objectMapper;
    private final QianxunProperties properties;

    public OpenAiCompatibleStreamClient(ObjectMapper objectMapper, QianxunProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * OpenAI 兼容：非流式 chat completions（用于 Hermes/LLM 上的 NLU 意图与槽位抽取）。
     */
    public String completeChat(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            double temperature
    ) throws Exception {
        String url = trimTrailingSlash(baseUrl) + "/chat/completions";

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", temperature);

        String json = objectMapper.writeValueAsString(body);

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        applyAuth(b, apiKey);

        HttpResponse<String> response = HTTP_CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM 非流式请求失败 HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode err = root.path("error");
        if (!err.isMissingNode() && err.isObject()) {
            throw new IllegalStateException("LLM 返回错误: " + err);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode contentNode = choices.get(0).path("message").path("content");
        return contentNode.isTextual() ? contentNode.asText("") : "";
    }

    /**
     * OpenAI 兼容：流式 chat completions（用于 Hermes Agent / vLLM / OpenAI 等）。
     * <p>
     * toolCallListener 可为 null：
     * - 当 delta 含 tool_calls 时调用（Hermes 执行 function calling 时触发）
     * - 当 SSE 事件为 hermes.tool.* 时调用（Hermes 工具进度/结果透传）
     */
    public StreamCompletionMeta streamChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            StreamTokenConsumer consumer
    ) throws Exception {
        return streamChatCompletions(baseUrl, apiKey, model, messages, consumer, null);
    }

    /**
     * OpenAI 兼容：流式 chat completions，带工具调用回调。
     *
     * @return 是否收到 [DONE]、以及最后一帧的 finish_reason（如 length 表示可能因 max_tokens 截断）
     */
    public StreamCompletionMeta streamChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            StreamTokenConsumer consumer,
            ToolCallListener toolCallListener
    ) throws Exception {
        String url = trimTrailingSlash(baseUrl) + "/chat/completions";

        int maxTok = Math.max(256, properties.getLlm().getMaxTokens());
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        body.put("max_tokens", maxTok);
        String json = objectMapper.writeValueAsString(body);

        int timeoutSec = Math.max(60, properties.getLlm().getStreamTimeoutSeconds());

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        applyAuth(b, apiKey);

        HttpResponse<InputStream> response = HTTP_CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("LLM 请求失败 HTTP " + response.statusCode() + ": " + err);
        }

        // 按 index 累积工具调用（函数名 + 参数片段）
        Map<Integer, ToolCallAcc> tcAccMap = new LinkedHashMap<>();

        boolean sawDone = false;
        IntAndString state = new IntAndString(0, null);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line = null;
            String eventName = "message";
            StringBuilder dataBuf = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (dataBuf.length() == 0) {
                        eventName = "message";
                        continue;
                    }

                    String payload = dataBuf.toString().trim();
                    dataBuf.setLength(0);
                    String currentEvent = eventName;
                    eventName = "message";

                    if ("[DONE]".equalsIgnoreCase(payload)) {
                        sawDone = true;
                        break;
                    }

                    if (toolCallListener != null && currentEvent.startsWith("hermes.tool.")) {
                        int seq = state.seq();
                        state = new IntAndString(handleHermesToolEvent(payload, toolCallListener, seq), state.finishReason());
                    } else {
                        state = processPayload(payload, tcAccMap, consumer, toolCallListener, state);
                    }
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (dataBuf.length() > 0) {
                        dataBuf.append('\n');
                    }
                    dataBuf.append(line.substring("data:".length()).trim());
                    continue;
                }
            }
        }

        if (!sawDone) {
            log.warn("LLM 流式响应未收到 [DONE]，连接可能已提前关闭；已输出内容可能不完整");
        }
        if ("length".equals(state.finishReason())) {
            log.warn("LLM finish_reason=length，可能因 max_tokens 截断。当前 qianxun.llm.max-tokens={}", maxTok);
        }

        return new StreamCompletionMeta(sawDone, state.finishReason());
    }

    private record IntAndString(int seq, String finishReason) {}

    private IntAndString processPayload(String payload, Map<Integer, ToolCallAcc> tcAccMap,
                                        StreamTokenConsumer consumer, ToolCallListener listener, IntAndString s) throws Exception {
        SseParseResult parsed = parseSsePayload(payload);
        if (parsed == null) return s;
        emitContentTokens(parsed.contentNode, consumer);
        int seq = s.seq();
        if (listener != null) {
            seq = parseToolCalls(parsed.toolCallsNode, tcAccMap, listener, seq);
        }
        String fr = (s.finishReason() != null && !s.finishReason().isBlank()) ? s.finishReason() : parsed.finishReason;
        return new IntAndString(seq, fr);
    }

    // ── SSE 解析辅助 ───────────────────────────────────────────────────────────

    private enum SseLineType { DATA, EVENT, BLANK, OTHER }

    private record SseParseResult(JsonNode contentNode, JsonNode toolCallsNode, String finishReason) {}

    private static SseLineType classifyLine(String line) {
        if (line.isBlank())             return SseLineType.BLANK;
        if (line.startsWith("event:"))  return SseLineType.EVENT;
        if (line.startsWith("data:"))   return SseLineType.DATA;
        return SseLineType.OTHER;
    }

    private SseParseResult parseSsePayload(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode errNode = root.path("error");
        if (!errNode.isMissingNode() && errNode.isObject()) {
            throw new IllegalStateException("LLM 返回错误: " + errNode);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return null;
        JsonNode choice0 = choices.get(0);
        JsonNode fr = choice0.path("finish_reason");
        String frText = (!fr.isMissingNode() && !fr.isNull() && fr.isTextual()) ? fr.asText() : null;
        return new SseParseResult(choice0.path("delta").path("content"), choice0.path("delta").path("tool_calls"), frText);
    }

    private int handleHermesToolEvent(String payload, ToolCallListener listener, int seq) {
        String toolName = "hermes_tool";
        String toolId = null;
        try {
            JsonNode n = objectMapper.readTree(payload);
            toolName = n.path("tool").asText(toolName);
            toolId = n.path("id").asText(null);
        } catch (Exception ignored) { /* 非 JSON 时透传 payload */ }
        if (toolId == null || toolId.isBlank()) { toolId = "hermes_" + toolName + "_" + (++seq); }
        listener.onToolCall(new ToolCallEvent(toolId, toolName, payload));
        return seq;
    }

    private int parseToolCalls(JsonNode toolCalls, Map<Integer, ToolCallAcc> accMap,
                               ToolCallListener listener, int seq) {
        if (!toolCalls.isArray()) { return seq; }
        for (JsonNode tc : toolCalls) {
            int idx = tc.path("index").asInt(0);
            ToolCallAcc acc = accMap.computeIfAbsent(idx, k -> new ToolCallAcc(new StringBuilder(), new StringBuilder()));
            String id    = tc.path("id").asText(null);
            JsonNode fn  = tc.path("function");
            String fname = fn.path("name").asText(null);
            String fargs = fn.path("arguments").asText(null);
            if (fname != null && !fname.isEmpty()) { acc.name().append(fname); }
            if (fargs != null)                    { acc.args().append(fargs); }
            String toolId = (id != null && !id.isEmpty()) ? id : ("call_" + idx);
            if ((fname != null && !fname.isEmpty()) || (fargs != null && !fargs.isEmpty() && !acc.name().isEmpty())) {
                listener.onToolCall(new ToolCallEvent(toolId, acc.name().toString(), acc.args().toString()));
            }
        }
        return seq;
    }

    private static String or(String existing, String newVal) {
        return (existing != null && !existing.isBlank()) ? existing : newVal;
    }

    private static void emitContentTokens(JsonNode contentNode, StreamTokenConsumer consumer) {
        if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
            return;
        }
        if (contentNode.isTextual()) {
            String token = contentNode.asText();
            if (!token.isEmpty()) {
                consumer.onToken(token);
            }
            return;
        }
        if (contentNode.isArray()) {
            for (JsonNode part : contentNode) {
                if (part == null) {
                    continue;
                }
                String t = part.path("text").asText("");
                if (t.isEmpty() && part.isTextual()) {
                    t = part.asText("");
                }
                if (!t.isEmpty()) {
                    consumer.onToken(t);
                }
            }
        }
    }

    public void streamMockReply(String userText, StreamTokenConsumer consumer) throws InterruptedException {
        QianxunProperties.Hermes hermes = properties.getHermes();
        String hermesHint = hermes.isEnabled()
                ? "提示：已启用 Hermes Agent 对接（qianxun.hermes.*）。配置 base-url / chat-model 后将走真实流式接口；NLU 会在流式输出前完成。\n\n"
                : "提示：设置环境变量 OPENAI_API_KEY，并配置 qianxun.llm.base-url / model，或启用 qianxun.hermes 对接本地/远端 Hermes。\n\n";

        String header = "（千寻 · 本地演示）当前未命中可用的远端推理端点（或显式开启 mock），以下为模拟流式输出。\n\n";
        String body = hermesHint + "你的问题：\n" + userText + "\n\n---\n\n";
        String entities = """

                ```qianxun-entities
                [{"name":"千寻","category":"org","type":"产品","description":"本地演示用占位实体"}]
                ```
                """;
        String full = header + body + entities;
        List<String> chunks = chunkText(full, 8);
        for (String c : chunks) {
            consumer.onToken(c);
            Thread.sleep(12);
        }
    }

    private static void applyAuth(HttpRequest.Builder b, String apiKey) {
        if (apiKey == null) {
            return;
        }
        String k = apiKey.trim();
        if (!k.isEmpty()) {
            b.header("Authorization", "Bearer " + k);
        }
    }

    private static List<String> chunkText(String text, int maxChars) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + maxChars);
            out.add(text.substring(i, end));
            i = end;
        }
        return out;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @FunctionalInterface
    public interface StreamTokenConsumer {
        void onToken(String token);
    }

    /** 工具调用事件（来自 delta.tool_calls，Hermes Agent 执行 function calling 时触发）。 */
    public record ToolCallEvent(
            String toolCallId,   // 工具调用 ID（call_xxx）
            String functionName, // 工具/函数名
            String argsChunk     // 参数增量（JSON 片段，可能多次累积）
    ) {}

    @FunctionalInterface
    public interface ToolCallListener {
        void onToolCall(ToolCallEvent event);
    }
}
