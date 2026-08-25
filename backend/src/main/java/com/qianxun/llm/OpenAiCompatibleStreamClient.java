package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleStreamClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleStreamClient.class);

    /** 流式结束元数据：是否收到 [DONE]、最后一帧的 finish_reason、token 用量 */
    public record StreamCompletionMeta(boolean sawDone, String finishReason, TokenUsage usage) {
        public StreamCompletionMeta(boolean sawDone, String finishReason) {
            this(sawDone, finishReason, null);
        }
    }

    /**
     * OpenAI 为单次请求用量；Dashboard {@code session.usage} / {@code message.complete.usage}
     * 为会话快照（{@code sessionSnapshot=true}），含 {@code context_used}/{@code context_percent}。
     */
    public record TokenUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer contextWindow,
            Integer contextUsed,
            Double contextPercent,
            boolean sessionSnapshot
    ) {
        public TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens, Integer contextWindow) {
            this(promptTokens, completionTokens, totalTokens, contextWindow, null, null, false);
        }
    }

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
     * OpenAI 兼容：非流式 chat completions（用于下一步建议等短请求）。
     */
    public String completeChat(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            double temperature
    ) throws Exception {
        return completeChat(baseUrl, apiKey, model, messages, temperature, Duration.ofMinutes(2));
    }

    public String completeChat(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            double temperature,
            Duration timeout
    ) throws Exception {
        String url = trimTrailingSlash(baseUrl) + "/chat/completions";

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", temperature);

        String json = objectMapper.writeValueAsString(body);

        Duration wait = timeout == null ? Duration.ofMinutes(2) : timeout;
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(wait)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        applyAuth(b, apiKey);

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException("上游 LLM 非流式请求超时（" + wait.toSeconds() + "s）: " + url, ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errBody = response.body();
            throw new IllegalStateException(
                    "LLM 非流式请求失败 HTTP " + response.statusCode() + ": " + errBody
                            + formatUpstreamAuthHint(baseUrl, response.statusCode(), apiKey, errBody));
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
        return streamChatCompletions(baseUrl, apiKey, model, messages, consumer, toolCallListener, null);
    }

    public StreamCompletionMeta streamChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            StreamTokenConsumer consumer,
            ToolCallListener toolCallListener,
            UsageListener usageListener
    ) throws Exception {
        return streamChatCompletions(
                baseUrl, apiKey, model, messages, consumer, toolCallListener, usageListener, null, null);
    }

    /**
     * @param cancelled        若返回 true，尽快结束读循环并抛出 CancellationException
     * @param registerCloseable 注册可关闭的上游 body，供外部 stop 时打断阻塞读
     */
    public StreamCompletionMeta streamChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            StreamTokenConsumer consumer,
            ToolCallListener toolCallListener,
            UsageListener usageListener,
            java.util.function.BooleanSupplier cancelled,
            java.util.function.Consumer<AutoCloseable> registerCloseable
    ) throws Exception {
        String url = trimTrailingSlash(baseUrl) + "/chat/completions";

        int maxTok = Math.max(256, properties.getLlm().getMaxTokens());
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        body.put("max_tokens", maxTok);
        body.put("stream_options", Map.of("include_usage", true));
        String json = objectMapper.writeValueAsString(body);

        int timeoutSec = properties.getLlm().getStreamTimeoutSeconds();

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        // JDK HttpRequest.timeout 是整段墙钟超时（含读完 body）。0/负数 = 不限制，避免智能体长轮次被掐断
        if (timeoutSec > 0) {
            b.timeout(Duration.ofSeconds(Math.max(60, timeoutSec)));
        }
        applyAuth(b, apiKey);

        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException("run cancelled");
        }

        HttpResponse<InputStream> response;
        try {
            response = HTTP_CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException(
                    "上游 LLM 流式请求超时（" + timeoutSec + "s）: " + url, ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    "LLM 请求失败 HTTP " + response.statusCode() + ": " + err
                            + formatUpstreamAuthHint(baseUrl, response.statusCode(), apiKey, err));
        }

        InputStream responseBody = response.body();
        if (registerCloseable != null) {
            registerCloseable.accept(responseBody);
        }

        // 按 index 累积工具调用（函数名 + 参数片段）
        Map<Integer, ToolCallAcc> tcAccMap = new LinkedHashMap<>();
        /** 同一工具名尚未结束的调用，避免 progress/complete / OpenAI tool_calls 各生成一条 */
        Map<String, String> openToolIdByName = new LinkedHashMap<>();

        boolean sawDone = false;
        IntAndString state = new IntAndString(0, null);
        TokenUsage[] usageHolder = new TokenUsage[1];

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody, StandardCharsets.UTF_8))) {
            String line = null;
            String eventName = "message";
            StringBuilder dataBuf = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new java.util.concurrent.CancellationException("run cancelled");
                }
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
                        state = new IntAndString(
                                handleHermesToolEvent(currentEvent, payload, toolCallListener, seq, openToolIdByName),
                                state.finishReason()
                        );
                    } else {
                        state = processPayload(payload, tcAccMap, consumer, toolCallListener, usageListener, usageHolder, state, openToolIdByName);
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

        return new StreamCompletionMeta(sawDone, state.finishReason(), usageHolder[0]);
    }

    private record IntAndString(int seq, String finishReason) {}

    private IntAndString processPayload(
            String payload,
            Map<Integer, ToolCallAcc> tcAccMap,
            StreamTokenConsumer consumer,
            ToolCallListener listener,
            UsageListener usageListener,
            TokenUsage[] usageHolder,
            IntAndString s,
            Map<String, String> openToolIdByName
    ) throws Exception {
        SseParseResult parsed = parseSsePayload(payload);
        if (parsed == null) {
            return s;
        }
        if (parsed.usage != null) {
            usageHolder[0] = parsed.usage;
            if (usageListener != null) {
                usageListener.onUsage(parsed.usage);
            }
        }
        emitContentTokens(parsed.contentNode, consumer);
        int seq = s.seq();
        if (listener != null) {
            seq = parseToolCalls(parsed.toolCallsNode, tcAccMap, listener, seq, openToolIdByName);
        }
        String fr = (s.finishReason() != null && !s.finishReason().isBlank()) ? s.finishReason() : parsed.finishReason;
        return new IntAndString(seq, fr);
    }

    // ── SSE 解析辅助 ───────────────────────────────────────────────────────────

    private enum SseLineType { DATA, EVENT, BLANK, OTHER }

    private record SseParseResult(JsonNode contentNode, JsonNode toolCallsNode, String finishReason, TokenUsage usage) {}

    private static SseLineType classifyLine(String line) {
        if (line.isBlank()) {
            return SseLineType.BLANK;
        }
        if (line.startsWith("event:")) {
            return SseLineType.EVENT;
        }
        if (line.startsWith("data:")) {
            return SseLineType.DATA;
        }
        return SseLineType.OTHER;
    }

    private SseParseResult parseSsePayload(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode errNode = root.path("error");
        if (!errNode.isMissingNode() && errNode.isObject()) {
            throw new IllegalStateException("LLM 返回错误: " + errNode);
        }
        TokenUsage usage = parseUsage(root.path("usage"));
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return usage == null ? null : new SseParseResult(null, null, null, usage);
        }
        JsonNode choice0 = choices.get(0);
        JsonNode fr = choice0.path("finish_reason");
        String frText = (!fr.isMissingNode() && !fr.isNull() && fr.isTextual()) ? fr.asText() : null;
        if (usage == null) {
            usage = parseUsage(choice0.path("usage"));
        }
        return new SseParseResult(choice0.path("delta").path("content"), choice0.path("delta").path("tool_calls"), frText, usage);
    }

    private TokenUsage parseUsage(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull() || !usage.isObject()) {
            return null;
        }
        Integer prompt = firstInt(usage, "prompt_tokens", "input_tokens");
        Integer completion = firstInt(usage, "completion_tokens", "output_tokens");
        Integer total = firstInt(usage, "total_tokens");
        Integer window = firstInt(usage, "context_window", "context_length", "model_context");
        if (prompt == null && completion == null && total == null) {
            return null;
        }
        return new TokenUsage(prompt, completion, total, window);
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

    private int handleHermesToolEvent(
            String eventName,
            String payload,
            ToolCallListener listener,
            int seq,
            Map<String, String> openToolIdByName
    ) {
        String toolName = "hermes_tool";
        String toolId = null;
        String args = "";
        String result = null;
        String status = statusFromHermesEvent(eventName);
        long now = System.currentTimeMillis();
        try {
            JsonNode n = objectMapper.readTree(payload);
            toolName = firstText(n, toolName, "tool", "name", "function");
            toolId = firstText(n, null, "id", "call_id", "tool_call_id");
            args = extractHermesArgs(n);
            JsonNode resultNode = firstNode(n, "result", "output", "content", "response");
            if (resultNode != null) {
                result = resultNode.isTextual() ? resultNode.asText() : resultNode.toString();
            }
            if (n.path("status").isTextual()) {
                status = n.path("status").asText(status);
            }
        } catch (Exception ignored) { /* 非 JSON 时透传 payload */ }
        String nameKey = toolName == null ? "" : toolName.trim();
        String openId = nameKey.isBlank() ? null : openToolIdByName.get(nameKey);
        if (openId != null && !openId.isBlank()) {
            toolId = openId;
        } else if (toolId == null || toolId.isBlank()) {
            toolId = "hermes_" + nameKey + "_" + (++seq);
        }
        boolean terminal = "completed".equals(status) || "error".equals(status);
        if (terminal) {
            openToolIdByName.remove(nameKey);
        } else if (!nameKey.isBlank()) {
            openToolIdByName.put(nameKey, toolId);
        }
        Long startedAt = terminal ? null : now;
        Long endedAt = terminal ? now : null;
        listener.onToolCall(new ToolCallEvent(toolId, toolName, args, result, status, startedAt, endedAt));
        return seq;
    }

    private static String statusFromHermesEvent(String eventName) {
        String e = eventName == null ? "" : eventName.toLowerCase();
        if (e.contains("complete") || e.endsWith(".done") || e.contains("result")) {
            return "completed";
        }
        if (e.contains("error") || e.contains("fail")) {
            return "error";
        }
        if (e.contains("start")) {
            return "started";
        }
        return "running";
    }

    private static final java.util.Set<String> HERMES_META_KEYS = java.util.Set.of(
            "tool", "name", "function", "id", "call_id", "tool_call_id", "toolCallId",
            "status", "preview", "type", "event", "result", "output", "response"
    );

    /** 只保留真正的调用参数，避免把整段 Hermes 事件当 args 回传 */
    private String extractHermesArgs(JsonNode n) {
        JsonNode argsNode = firstNode(n, "args", "arguments", "input", "params");
        if (argsNode != null) {
            return argsNode.isTextual() ? argsNode.asText() : argsNode.toString();
        }
        if (n.path("preview").isTextual() && !n.path("preview").asText().isBlank()) {
            return n.path("preview").asText();
        }
        if (n.isObject()) {
            ObjectNode copy = n.deepCopy();
            copy.remove(HERMES_META_KEYS);
            if (!copy.isEmpty()) {
                return copy.toString();
            }
        }
        return "";
    }

    private static String firstText(JsonNode n, String fallback, String... keys) {
        for (String k : keys) {
            String v = n.path(k).asText("");
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return fallback;
    }

    private static JsonNode firstNode(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (!v.isMissingNode() && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    private int parseToolCalls(
            JsonNode toolCalls,
            Map<Integer, ToolCallAcc> accMap,
            ToolCallListener listener,
            int seq,
            Map<String, String> openToolIdByName
    ) {
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
            String nameKey = acc.name().toString().trim();
            String openId = nameKey.isBlank() ? null : openToolIdByName.get(nameKey);
            String toolId = (openId != null && !openId.isBlank())
                    ? openId
                    : ((id != null && !id.isEmpty()) ? id : ("call_" + idx));
            if (!nameKey.isBlank()) {
                openToolIdByName.put(nameKey, toolId);
            }
            if ((fname != null && !fname.isEmpty()) || (fargs != null && !fargs.isEmpty() && !acc.name().isEmpty())) {
                listener.onToolCall(new ToolCallEvent(
                        toolId, acc.name().toString(), acc.args().toString(),
                        null, "running", System.currentTimeMillis(), null
                ));
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
        streamMockReply(userText, consumer, null);
    }

    public void streamMockReply(
            String userText,
            StreamTokenConsumer consumer,
            java.util.function.BooleanSupplier cancelled
    ) throws InterruptedException {
        QianxunProperties.Claude claude = properties.getClaude();
        String hint = claude.isEnabled()
                ? "提示：已启用 Claude Code 运行器（qianxun.claude.*）。配置 ANTHROPIC_API_KEY 后将走真实智能体。\n\n"
                : "提示：设置环境变量 ANTHROPIC_API_KEY，并配置 qianxun.claude.* 启用 Claude Code 运行器。\n\n";

        String header = "（千寻 · 本地演示）当前未命中可用的远端推理端点（或显式开启 mock），以下为模拟流式输出。\n\n";
        String body = hint + "你的问题：\n" + userText + "\n\n---\n\n";
        String full = header + body;
        List<String> chunks = chunkText(full, 8);
        for (String c : chunks) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new java.util.concurrent.CancellationException("run cancelled");
            }
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

    /**
     * 上游返回 401 / incorrect_api_key 时追加排查说明（千寻自身接口一般不返回 401，多为 LLM 网关鉴权失败）。
     */
    private static String formatUpstreamAuthHint(String baseUrl, int status, String apiKey, String responseBody) {
        if (status != 401 && (responseBody == null || !responseBody.contains("incorrect_api_key"))) {
            return "";
        }
        String host = safeHostForHint(baseUrl);
        boolean sentBearer = apiKey != null && !apiKey.trim().isEmpty();
        return """

                【排查】上游 LLM 鉴权失败（非千寻业务接口 401）：
                · 请求目标 host=%s，是否已带 Bearer：%s
                · Hermes：环境变量 HERMES_API_KEY（或 docker 中 API_SERVER_KEY）须与 Hermes 容器 API_SERVER_KEY 一致；模型注册表项 base_url 若指向 Hermes，须与 qianxun.hermes.base-url 规范化后一致，否则会误用 OPENAI_API_KEY 导致 401。
                · 直连 OpenAI（如注册模型 gpt-4o-mini）：须配置有效 OPENAI_API_KEY，勿把 Kimi 密钥填在 OPENAI 变量里。
                · 直连 Moonshot（如 kimi-* 模型）：须配置 KIMI_API_KEY（或可用的 qianxun.llm.api-key）。
                """.formatted(host, sentBearer ? "是" : "否（未发送 Authorization，部分网关会 401）");
    }

    private static String safeHostForHint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "(空)";
        }
        try {
            URI u = URI.create(baseUrl.trim());
            String h = u.getHost();
            return h != null ? h : baseUrl.trim();
        } catch (Exception e) {
            return "(无法解析)";
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

    /**
     * 工具调用事件（OpenAI delta.tool_calls 或 Dashboard {@code tool.*}）。
     * {@code details} 承载上游附加字段（context / summary / risk 等），供 SSE 原样转发。
     */
    public record ToolCallEvent(
            String toolCallId,
            String functionName,
            String argsChunk,
            String result,
            String status,
            Long startedAt,
            Long endedAt,
            Map<String, Object> details
    ) {
        public ToolCallEvent(String toolCallId, String functionName, String argsChunk) {
            this(toolCallId, functionName, argsChunk, null, "running", null, null, null);
        }

        public ToolCallEvent(
                String toolCallId,
                String functionName,
                String argsChunk,
                String result,
                String status,
                Long startedAt,
                Long endedAt
        ) {
            this(toolCallId, functionName, argsChunk, result, status, startedAt, endedAt, null);
        }
    }

    @FunctionalInterface
    public interface ToolCallListener {
        void onToolCall(ToolCallEvent event);
    }

    @FunctionalInterface
    public interface UsageListener {
        void onUsage(TokenUsage usage);
    }
}
