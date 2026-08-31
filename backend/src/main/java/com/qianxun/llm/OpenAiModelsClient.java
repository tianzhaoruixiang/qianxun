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
        JsonNode info = fetchJsonQuiet(modelInfoUrl(baseUrl), apiKey);
        if (info != null) {
            items = mergeWindows(items, parseModelInfos(info));
        }
        return enrichKnownWindows(items);
    }

    /**
     * 查当前模型上下文窗口：上游 /models、LiteLLM /model/info，再对常用公开模型做只读兜底。
     * 失败返回 0，不抛给聊天主路径。
     */
    public int findContextWindow(String baseUrl, String apiKey, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return 0;
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            try {
                JsonNode root = fetchModelsJson(baseUrl, apiKey);
                int w = findContextWindowInList(root, modelId);
                if (w > 0) {
                    return w;
                }
                JsonNode info = fetchJsonQuiet(modelInfoUrl(baseUrl), apiKey);
                w = findContextWindowInList(info, modelId);
                if (w > 0) {
                    return w;
                }
            } catch (Exception ex) {
                // 走公开模型兜底
            }
        }
        return KnownModelContextWindows.lookup(modelId);
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

    /** LiteLLM {@code /v1/model/info}：失败返回 null，不影响 /models 列表。 */
    private JsonNode fetchJsonQuiet(String endpoint, String apiKey) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String cacheKey = endpoint + "\0" + (apiKey == null ? "" : apiKey.trim());
        CachedRoot hit = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expiresAt > now) {
            return hit.root();
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(TIMEOUT)
                    .GET()
                    .header("Accept", "application/json");
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey.trim());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            cache.put(cacheKey, new CachedRoot(root, now + CACHE_TTL_MS));
            return root;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    static String modelsUrl(String baseUrl) {
        String b = trimBase(baseUrl);
        if (b.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 不能为空");
        }
        String lower = b.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/models")) {
            return b;
        }
        return b + "/models";
    }

    static String modelInfoUrl(String baseUrl) {
        String b = trimBase(baseUrl);
        if (b.isEmpty()) {
            return "";
        }
        String lower = b.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/model/info")) {
            return b;
        }
        if (lower.endsWith("/models")) {
            return b.substring(0, b.length() - "/models".length()) + "/model/info";
        }
        return b + "/model/info";
    }

    private static String trimBase(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
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
            if (!itemMatches(item, modelId)) {
                continue;
            }
            int w = parseContextWindow(item);
            if (w > 0) {
                return w;
            }
        }
        return 0;
    }

    static boolean itemMatches(JsonNode item, String modelId) {
        if (idsMatch(itemId(item), modelId)) {
            return true;
        }
        JsonNode params = item.get("litellm_params");
        if (params != null && params.isObject()) {
            JsonNode nested = params.get("model");
            if (nested != null && nested.isTextual() && idsMatch(nested.asText(), modelId)) {
                return true;
            }
        }
        return false;
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
        for (String field : new String[] {"id", "model_name", "name"}) {
            JsonNode v = item.get(field);
            if (v != null && v.isTextual() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return "";
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
            int i = asPositiveInt(n.get(f));
            if (i > 0) {
                return i;
            }
        }
        return 0;
    }

    private static int asPositiveInt(JsonNode v) {
        if (v == null || v.isNull()) {
            return 0;
        }
        if (v.isNumber()) {
            int i = v.asInt();
            return i > 0 ? i : 0;
        }
        if (v.isTextual()) {
            String s = v.asText().trim().replace("_", "").replace(",", "");
            if (s.isEmpty()) {
                return 0;
            }
            try {
                int i = Integer.parseInt(s);
                return i > 0 ? i : 0;
            } catch (NumberFormatException ex) {
                return 0;
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
                addInfo(seen, items, itemId(item), parseContextWindow(item));
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
        int window = contextWindow > 0 ? contextWindow : KnownModelContextWindows.lookup(v);
        items.add(new OpenAiModelInfo(v, window));
    }

    static List<OpenAiModelInfo> mergeWindows(List<OpenAiModelInfo> primary, List<OpenAiModelInfo> extra) {
        if (extra == null || extra.isEmpty()) {
            return primary;
        }
        List<OpenAiModelInfo> out = new ArrayList<>(primary.size());
        for (OpenAiModelInfo item : primary) {
            int w = item.contextWindow();
            if (w <= 0) {
                for (OpenAiModelInfo other : extra) {
                    if (idsMatch(item.id(), other.id()) && other.contextWindow() > 0) {
                        w = other.contextWindow();
                        break;
                    }
                }
            }
            out.add(w == item.contextWindow() ? item : new OpenAiModelInfo(item.id(), w));
        }
        return List.copyOf(out);
    }

    static List<OpenAiModelInfo> enrichKnownWindows(List<OpenAiModelInfo> items) {
        List<OpenAiModelInfo> out = new ArrayList<>(items.size());
        for (OpenAiModelInfo item : items) {
            int w = item.contextWindow() > 0 ? item.contextWindow() : KnownModelContextWindows.lookup(item.id());
            out.add(w == item.contextWindow() ? item : new OpenAiModelInfo(item.id(), w));
        }
        return List.copyOf(out);
    }

    private static String brief(String message) {
        if (message == null || message.isBlank()) {
            return "网络错误";
        }
        String v = message.replace('\n', ' ').trim();
        return v.length() > 160 ? v.substring(0, 160) : v;
    }
}
