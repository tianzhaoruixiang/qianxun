package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
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
     * toolCallListener 可为 null，当 delta 含 tool_calls 时调用（Hermes 执行 function calling 时触发）。
     */
    public void streamChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            StreamTokenConsumer consumer
    ) throws Exception {
        streamChatCompletions(baseUrl, apiKey, model, messages, consumer, null);
    }

    /**
     * OpenAI 兼容：流式 chat completions，带工具调用回调。
     */
    public void streamChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            StreamTokenConsumer consumer,
            ToolCallListener toolCallListener
    ) throws Exception {
        String url = trimTrailingSlash(baseUrl) + "/chat/completions";

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", true
        );
        String json = objectMapper.writeValueAsString(body);

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
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
        record ToolCallAcc(StringBuilder name, StringBuilder args) {}
        java.util.Map<Integer, ToolCallAcc> tcAccMap = new java.util.LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if ("[DONE]".equalsIgnoreCase(payload)) {
                    return;
                }
                JsonNode root = objectMapper.readTree(payload);
                JsonNode errNode = root.path("error");
                if (!errNode.isMissingNode() && errNode.isObject()) {
                    throw new IllegalStateException("LLM 返回错误: " + errNode);
                }
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    continue;
                }
                JsonNode delta = choices.get(0).path("delta");

                // ── 普通文本 token ──
                JsonNode contentNode = delta.path("content");
                if (contentNode.isTextual()) {
                    String token = contentNode.asText();
                    if (!token.isEmpty()) {
                        consumer.onToken(token);
                    }
                }

                // ── 工具调用 delta ──
                if (toolCallListener != null) {
                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray()) {
                        for (JsonNode tc : toolCalls) {
                            int idx = tc.path("index").asInt(0);
                            ToolCallAcc acc = tcAccMap.computeIfAbsent(idx,
                                    k -> new ToolCallAcc(new StringBuilder(), new StringBuilder()));

                            String id    = tc.path("id").asText(null);
                            JsonNode fn  = tc.path("function");
                            String fname = fn.path("name").asText(null);
                            String fargs = fn.path("arguments").asText(null);

                            if (fname != null && !fname.isEmpty()) {
                                acc.name().append(fname);
                            }
                            if (fargs != null) {
                                acc.args().append(fargs);
                            }

                            // 工具名首次完整出现时立刻通知（name 完整出现在第一个包含 name 的 chunk）
                            if (fname != null && !fname.isEmpty()) {
                                String toolId = (id != null && !id.isEmpty()) ? id : ("call_" + idx);
                                toolCallListener.onToolCall(new ToolCallEvent(toolId, acc.name().toString(), acc.args().toString()));
                            } else if (fargs != null && !fargs.isEmpty() && !acc.name().isEmpty()) {
                                // 后续只有 args chunk，更新累积参数
                                String toolId = "call_" + idx;
                                toolCallListener.onToolCall(new ToolCallEvent(toolId, acc.name().toString(), acc.args().toString()));
                            }
                        }
                    }
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
        String full = header + body;
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
