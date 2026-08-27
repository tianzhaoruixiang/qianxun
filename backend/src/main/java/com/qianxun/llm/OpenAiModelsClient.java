package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OpenAiModelsClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(12);
    private static final long CACHE_TTL_MS = 30_000;

    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final ConcurrentHashMap<String, CachedRoot> cache = new ConcurrentHashMap<>();

    public OpenAiModelsClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    private record CachedRoot(JsonNode root, long expiresAt) {
    }

    public record OpenAiModelInfo(String id, int contextWindow) {
    }

    public List<String> listModelIds(String baseUrl, String apiKey) {
        return listModelInfos(baseUrl, apiKey).stream().map(OpenAiModelInfo::id).toList();
    }

    public List<OpenAiModelInfo> listModelInfos(String baseUrl, String apiKey) {
        JsonNode root = fetchModelsJson(baseUrl, apiKey);
        List<OpenAiModelInfo> items = parseModelInfos(root);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "上游 /models 未返回任何模型 id");
        }
        return items;
    }

    /**
     * 对话压缩用：查当前模型在上游 /models 中声明的上下文窗口。失败返回 0，不抛给聊天主路径。
     */
    public int findContextWindow(String baseUrl, String apiKey, String modelId) {
        if (baseUrl == null || baseUrl.isBlank() || modelId == null || modelId.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = fetchModelsJson(baseUrl, apiKey);
            return findContextWindowInList(root, modelId);
        } catch (Exception ex) {
            return 0;
        }
    }

    private JsonNode fetchModelsJson(String baseUrl, String apiKey) {
        String endpoint = modelsUrl(baseUrl);
        String cacheKey = endpoint + "\0" + (apiKey == null ? "" : apiKey.trim());
        CachedRoot hit = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expiresAt > now) {
            return hit.root();
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(TIMEOUT)
                .GET()
                .header("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "查询上游模型被中断");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接上游 /models：" + brief(ex.getMessage()));
        }
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "上游 /models 返回 HTTP " + status + (status == 401 || status == 403 ? "（密钥无效或无权限）" : ""));
        }
        try {
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            cache.put(cacheKey, new CachedRoot(root, now + CACHE_TTL_MS));
            return root;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "上游 /models 响应不是合法 JSON");
        }
    }

    static String modelsUrl(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        if (b.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 不能为空");
        }
        String lower = b.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/models")) {
            return b;
        }
        return b + "/models";
    }

    static List<String> parseModelIds(JsonNode root) {
        return parseModelInfos(root).stream().map(OpenAiModelInfo::id).toList();
    }

    static List<OpenAiModelInfo> parseModelInfos(JsonNode root) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<OpenAiModelInfo> items = new ArrayList<>();
        if (root == null || root.isNull()) {
            return List.of();
        }
        if (root.isArray()) {
            collectInfos(root, seen, items);
            return List.copyOf(items);
        }
        collectInfos(root.get("data"), seen, items);
        collectInfos(root.get("models"), seen, items);
        JsonNode id = root.get("id");
        if (id != null && id.isTextual()) {
            addInfo(seen, items, id.asText(), parseContextWindow(root));
        }
        return List.copyOf(items);
    }

    static int findContextWindowInList(JsonNode root, String modelId) {
        if (root == null || modelId == null || modelId.isBlank()) {
            return 0;
        }
        List<JsonNode> items = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(items::add);
        } else if (root.isObject()) {
            addArrayItems(root.get("data"), items);
            addArrayItems(root.get("models"), items);
            items.add(root);
        }
        for (JsonNode item : items) {
            if (item == null || !item.isObject()) {
                continue;
            }
            if (!idsMatch(itemId(item), modelId)) {
                continue;
            }
            int w = parseContextWindow(item);
            if (w > 0) {
                return w;
            }
        }
        return 0;
    }

    static int parseContextWindow(JsonNode item) {
        int w = firstPositiveInt(item, "context_window", "contextWindow", "context_length",
                "max_model_len", "max_input_tokens", "max_input");
        if (w > 0) {
            return w;
        }
        for (String nested : new String[] {"model_info", "metadata", "info"}) {
            JsonNode child = item.get(nested);
            if (child != null && child.isObject()) {
                w = firstPositiveInt(child, "max_input_tokens", "max_tokens", "context_window", "max_model_len");
                if (w > 0) {
                    return w;
                }
            }
        }
        return 0;
    }

    private static void addArrayItems(JsonNode node, List<JsonNode> items) {
        if (node != null && node.isArray()) {
            node.forEach(items::add);
        }
    }

    private static String itemId(JsonNode item) {
        JsonNode id = item.get("id");
        if (id != null && id.isTextual()) {
            return id.asText();
        }
        JsonNode name = item.get("name");
        return name != null && name.isTextual() ? name.asText() : "";
    }

    static boolean idsMatch(String listed, String wanted) {
        String a = normalizeModelId(listed);
        String b = normalizeModelId(wanted);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b) || a.endsWith("/" + b) || b.endsWith("/" + a);
    }

    private static String normalizeModelId(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        int slash = v.indexOf('/');
        if (slash > 0 && slash < v.length() - 1) {
            String prefix = v.substring(0, slash);
            if ("openai".equals(prefix) || "anthropic".equals(prefix) || "litellm".equals(prefix)) {
                return v.substring(slash + 1);
            }
        }
        return v;
    }

    private static int firstPositiveInt(JsonNode n, String... fields) {
        if (n == null || !n.isObject()) {
            return 0;
        }
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && v.isNumber()) {
                int i = v.asInt();
                if (i > 0) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static void collectInfos(JsonNode node, Set<String> seen, List<OpenAiModelInfo> items) {
        if (node == null || node.isNull() || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isTextual()) {
                addInfo(seen, items, item.asText(), 0);
            } else if (item.isObject()) {
                JsonNode id = item.get("id");
                String raw = id != null && id.isTextual() ? id.asText() : "";
                if (raw.isEmpty()) {
                    JsonNode name = item.get("name");
                    raw = name != null && name.isTextual() ? name.asText() : "";
                }
                addInfo(seen, items, raw, parseContextWindow(item));
            }
        }
    }

    private static void addInfo(Set<String> seen, List<OpenAiModelInfo> items, String raw, int contextWindow) {
        if (raw == null) {
            return;
        }
        String v = raw.trim();
        if (v.isEmpty() || !seen.add(v)) {
            return;
        }
        items.add(new OpenAiModelInfo(v, contextWindow));
    }

    private static String brief(String message) {
        if (message == null || message.isBlank()) {
            return "网络错误";
        }
        String v = message.replace('\n', ' ').trim();
        return v.length() > 160 ? v.substring(0, 160) : v;
    }
}
