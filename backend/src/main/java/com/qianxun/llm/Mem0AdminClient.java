package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用本地 Mem0 OSS：热更新嵌入模型（{@code POST /configure/embedder}）。
 */
@Component
public class Mem0AdminClient {

    private static final Logger log = LoggerFactory.getLogger(Mem0AdminClient.class);

    private final QianxunProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Mem0AdminClient(QianxunProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        String base = properties.getMem0().resolvedBaseUrl();
        return properties.getMem0().isEnabled() && !base.isBlank();
    }

    /**
     * @return 警告信息；空字符串表示成功或未启用跳过
     */
    public String applyEmbedder(String model, int dims, String openaiBaseUrl, String openaiApiKey, String llmModel) {
        if (!isConfigured()) {
            return "";
        }
        String base = properties.getMem0().resolvedBaseUrl();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("embedding_dims", dims);
            if (openaiBaseUrl != null && !openaiBaseUrl.isBlank()) {
                body.put("openai_base_url", openaiBaseUrl.trim().replaceAll("/+$", ""));
            }
            if (openaiApiKey != null && !openaiApiKey.isBlank()) {
                body.put("openai_api_key", openaiApiKey.trim());
            }
            if (llmModel != null && !llmModel.isBlank()) {
                body.put("llm_model", llmModel.trim());
            }
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/configure/embedder"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                log.info("[mem0] embedder applied model={} dims={} status={}", model, dims, res.statusCode());
                return "";
            }
            String detail = extractDetail(res.body());
            log.warn("[mem0] embedder apply failed status={} body={}", res.statusCode(), abbreviate(res.body()));
            return "Mem0 嵌入模型热更新失败（HTTP " + res.statusCode() + "）" + (detail.isEmpty() ? "" : "：" + detail);
        } catch (Exception ex) {
            log.warn("[mem0] embedder apply error: {}", ex.toString());
            return "Mem0 不可达或热更新失败：" + ex.getMessage();
        }
    }

    private String extractDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("detail")) {
                return node.get("detail").asText("");
            }
            if (node.has("message")) {
                return node.get("message").asText("");
            }
        } catch (Exception ignored) {
            /* ignore */
        }
        return abbreviate(body);
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 180 ? t.substring(0, 180) + "…" : t;
    }
}
