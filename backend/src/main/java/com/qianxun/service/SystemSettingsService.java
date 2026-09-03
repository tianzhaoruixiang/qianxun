package com.qianxun.service;

import com.qianxun.config.QianxunProperties;
import com.qianxun.context.UserContext;
import com.qianxun.llm.Mem0AdminClient;
import com.qianxun.llm.OpenAiModelsClient;
import com.qianxun.repo.UiConfigRepository;
import com.qianxun.web.dto.ListOpenAiModelsRequest;
import com.qianxun.web.dto.OpenAiModelItemResponse;
import com.qianxun.web.dto.OpenAiModelListResponse;
import com.qianxun.web.dto.SystemSettingsResponse;
import com.qianxun.web.dto.UpdateSystemSettingsRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;

@Service
public class SystemSettingsService {

    public static final String KEY_SYSTEM_NAME = "system.name";
    public static final String KEY_CLAUDE_CHAT_MODEL = "claude.chat_model";
    public static final String KEY_OPENAI_BASE_URL = "openai.upstream_base_url";
    public static final String KEY_OPENAI_API_KEY = "openai.upstream_api_key";
    public static final String KEY_MEM0_EMBEDDER_MODEL = "mem0.embedder_model";
    public static final String KEY_MEM0_EMBEDDING_DIMS = "mem0.embedding_dims";
    public static final String DEFAULT_SYSTEM_NAME = "数智干警";

    private static final int NAME_MAX = 32;
    private static final int MODEL_MAX = 128;
    private static final int URL_MAX = 512;
    private static final int KEY_MAX = 512;

    private final UiConfigRepository uiConfigRepository;
    private final QianxunProperties properties;
    private final OpenAiModelsClient openAiModelsClient;
    private final Mem0AdminClient mem0AdminClient;

    public SystemSettingsService(
            UiConfigRepository uiConfigRepository,
            QianxunProperties properties,
            OpenAiModelsClient openAiModelsClient,
            Mem0AdminClient mem0AdminClient
    ) {
        this.uiConfigRepository = uiConfigRepository;
        this.properties = properties;
        this.openAiModelsClient = openAiModelsClient;
        this.mem0AdminClient = mem0AdminClient;
    }

    public String resolvedSystemName() {
        return uiConfigRepository.findValue(KEY_SYSTEM_NAME)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(DEFAULT_SYSTEM_NAME);
    }

    /**
     * 管理员配置的 <b>LiteLLM 上游最终模型</b>（厂商 /v1 的 model id），
     * 不是网关别名 {@code openai-default}。
     */
    public String resolvedClaudeChatModel() {
        String fromUi = uiConfigRepository.findValue(KEY_CLAUDE_CHAT_MODEL)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse("");
        String fromEnv = blankOr(properties.getClaude().getChatModel(), "qwen3.6-plus");
        return normalizeUpstreamModel(fromUi.isEmpty() ? fromEnv : fromUi, fromEnv);
    }

    public String resolvedOpenaiBaseUrl() {
        String fromUi = stored(KEY_OPENAI_BASE_URL);
        if (!fromUi.isEmpty()) {
            return rewriteLoopbackHost(fromUi);
        }
        return rewriteLoopbackHost(blankOr(properties.getClaude().getOpenaiUpstreamBaseUrl(), ""));
    }

    public String resolvedOpenaiApiKey() {
        String fromUi = stored(KEY_OPENAI_API_KEY);
        if (!fromUi.isEmpty()) {
            return fromUi;
        }
        return blankOr(properties.getClaude().getOpenaiUpstreamApiKey(), "");
    }

    public String resolvedMem0EmbedderModel() {
        String fromUi = stored(KEY_MEM0_EMBEDDER_MODEL);
        if (!fromUi.isEmpty()) {
            return stripProviderPrefix(fromUi);
        }
        return stripProviderPrefix(blankOr(properties.getMem0().getEmbedderModel(), "text-embedding-v3"));
    }

    public int resolvedMem0EmbeddingDims() {
        String fromUi = stored(KEY_MEM0_EMBEDDING_DIMS);
        if (!fromUi.isEmpty()) {
            try {
                return clampDims(Integer.parseInt(fromUi));
            } catch (NumberFormatException ignored) {
                /* fall through */
            }
        }
        return clampDims(properties.getMem0().getEmbeddingDims());
    }

    public SystemSettingsResponse snapshot() {
        return snapshot("");
    }

    public SystemSettingsResponse snapshot(String mem0ApplyWarning) {
        boolean admin = UserContext.isAdmin();
        String key = admin ? resolvedOpenaiApiKey() : "";
        String model = resolvedClaudeChatModel();
        Integer window = admin ? positiveOrNull(openAiModelsClient.findContextWindow(
                resolvedOpenaiBaseUrl(), resolvedOpenaiApiKey(), model)) : null;
        return new SystemSettingsResponse(
                resolvedSystemName(),
                model,
                admin ? resolvedOpenaiBaseUrl() : "",
                admin ? maskApiKey(key) : "",
                admin && !key.isEmpty(),
                window,
                admin ? resolvedMem0EmbedderModel() : "",
                admin ? resolvedMem0EmbeddingDims() : null,
                admin ? (mem0ApplyWarning == null ? "" : mem0ApplyWarning) : ""
        );
    }

    public SystemSettingsResponse update(UpdateSystemSettingsRequest body) {
        if (!UserContext.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可修改系统名称与上游 OpenAI 配置");
        }
        String name = body == null || body.systemName() == null ? "" : body.systemName().trim();
        String model = body == null || body.claudeChatModel() == null ? "" : body.claudeChatModel().trim();
        String baseUrl = body == null || body.openaiBaseUrl() == null ? "" : body.openaiBaseUrl().trim();
        String apiKey = body == null || body.openaiApiKey() == null ? "" : body.openaiApiKey().trim();
        String embedderModel = body == null || body.mem0EmbedderModel() == null
                ? "" : body.mem0EmbedderModel().trim();
        Integer embedderDims = body == null ? null : body.mem0EmbeddingDims();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统名称不能为空");
        }
        if (name.length() > NAME_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统名称最长 " + NAME_MAX + " 字");
        }
        if (model.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型不能为空");
        }
        if (model.length() > MODEL_MAX || !model.chars().allMatch(SystemSettingsService::allowedModelChar)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型标识格式无效");
        }
        if (baseUrl.length() > URL_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 过长");
        }
        if (!baseUrl.isEmpty()) {
            baseUrl = requireHttpUrl(baseUrl);
        }
        if (apiKey.length() > KEY_MAX || apiKey.chars().anyMatch(c -> c < 32)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API Key 格式无效");
        }
        if (!embedderModel.isEmpty()) {
            if (embedderModel.length() > MODEL_MAX
                    || !embedderModel.chars().allMatch(SystemSettingsService::allowedModelChar)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "嵌入模型标识格式无效");
            }
            embedderModel = stripProviderPrefix(embedderModel);
            uiConfigRepository.upsert(KEY_MEM0_EMBEDDER_MODEL, embedderModel);
        }
        if (embedderDims != null) {
            uiConfigRepository.upsert(KEY_MEM0_EMBEDDING_DIMS, String.valueOf(clampDims(embedderDims)));
        }
        String fromEnv = blankOr(properties.getClaude().getChatModel(), "qwen3.6-plus");
        model = normalizeUpstreamModel(model, fromEnv);
        uiConfigRepository.upsert(KEY_SYSTEM_NAME, name);
        uiConfigRepository.upsert(KEY_CLAUDE_CHAT_MODEL, model);
        uiConfigRepository.upsert(KEY_OPENAI_BASE_URL, rewriteLoopbackHost(baseUrl));
        if (!apiKey.isEmpty() && !looksMasked(apiKey)) {
            uiConfigRepository.upsert(KEY_OPENAI_API_KEY, apiKey);
        }

        String warning = applyMem0EmbedderIfNeeded();
        return snapshot(warning);
    }

    String applyMem0EmbedderIfNeeded() {
        if (!mem0AdminClient.isConfigured()) {
            return "";
        }
        String embedder = resolvedMem0EmbedderModel();
        int dims = resolvedMem0EmbeddingDims();
        String upstreamBase = resolvedOpenaiBaseUrl();
        // 优先系统设置上游（任意 embedding id 可直打厂商）；未配置时回退 Mem0 专用 Base URL（如 LiteLLM）
        String mem0OpenaiBase = upstreamBase.isEmpty()
                ? rewriteLoopbackHost(blankOr(properties.getMem0().getOpenaiBaseUrl(), ""))
                : upstreamBase;
        String key = resolvedOpenaiApiKey();
        return mem0AdminClient.applyEmbedder(
                embedder,
                dims,
                mem0OpenaiBase,
                key,
                resolvedClaudeChatModel()
        );
    }

    public OpenAiModelListResponse listUpstreamModels(ListOpenAiModelsRequest body) {
        if (!UserContext.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可查询上游模型列表");
        }
        String fromForm = body == null || body.openaiBaseUrl() == null ? "" : body.openaiBaseUrl().trim();
        String baseUrl = fromForm.isEmpty() ? resolvedOpenaiBaseUrl() : requireHttpUrl(fromForm);
        if (baseUrl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先填写 Base URL");
        }
        String fromFormKey = body == null || body.openaiApiKey() == null ? "" : body.openaiApiKey().trim();
        String apiKey = fromFormKey.isEmpty() || looksMasked(fromFormKey)
                ? resolvedOpenaiApiKey()
                : fromFormKey;
        var infos = openAiModelsClient.listModelInfos(baseUrl, apiKey);
        return new OpenAiModelListResponse(
                infos.stream().map(OpenAiModelsClient.OpenAiModelInfo::id).toList(),
                infos.stream()
                        .map(i -> new OpenAiModelItemResponse(i.id(), i.contextWindow() > 0 ? i.contextWindow() : null))
                        .toList()
        );
    }

    /** 常见嵌入模型默认维数；未知则保持当前已解析维数。 */
    public static int suggestEmbeddingDims(String model, int fallback) {
        String m = stripProviderPrefix(model).toLowerCase(Locale.ROOT);
        if (m.contains("text-embedding-3-large")) {
            return 3072;
        }
        if (m.contains("text-embedding-3-small") || m.contains("text-embedding-ada") || m.equals("text-embedding-v2")) {
            return 1536;
        }
        if (m.contains("text-embedding-v3") || m.contains("text-embedding-v4")) {
            return 1024;
        }
        return clampDims(fallback);
    }

    static int clampDims(int dims) {
        return Math.max(64, Math.min(8192, dims));
    }

    static String normalizeUpstreamModel(String raw, String envFallback) {
        String value = stripProviderPrefix(raw);
        if (value.isEmpty() || isLiteLlmGatewayAlias(value)) {
            value = stripProviderPrefix(envFallback);
        }
        if (value.isEmpty() || isLiteLlmGatewayAlias(value)) {
            return "qwen3.6-plus";
        }
        return value;
    }

    static boolean isLiteLlmGatewayAlias(String model) {
        String n = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
        return n.equals("openai-default") || n.equals("openai-compat") || n.equals("litellm");
    }

    static String stripProviderPrefix(String model) {
        if (model == null) {
            return "";
        }
        String v = model.trim();
        int slash = v.indexOf('/');
        if (slash > 0 && slash < v.length() - 1) {
            String provider = v.substring(0, slash).toLowerCase(Locale.ROOT);
            if (provider.equals("openai") || provider.equals("anthropic")) {
                return v.substring(slash + 1).trim();
            }
        }
        return v;
    }

    static String requireHttpUrl(String raw) {
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 不是合法地址");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 须为 http 或 https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 缺少主机名");
        }
        return rewriteLoopbackHost(raw.trim());
    }

    static String rewriteLoopbackHost(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException ex) {
            return raw.trim();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("127.0.0.1") && !host.equals("localhost") && !host.equals("::1") && !host.equals("0.0.0.0")) {
            return raw.trim().replaceAll("/+$", "");
        }
        try {
            URI rewritten = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    "host.docker.internal",
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
            return rewritten.toString().replaceAll("/+$", "");
        } catch (Exception ex) {
            return raw.trim().replaceAll("/+$", "");
        }
    }

    static String maskApiKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String v = key.trim();
        if (v.length() <= 8) {
            return "********";
        }
        return v.substring(0, 4) + "********" + v.substring(v.length() - 4);
    }

    static boolean looksMasked(String key) {
        return key != null && key.contains("********");
    }

    private static Integer positiveOrNull(int window) {
        return window > 0 ? window : null;
    }

    private String stored(String key) {
        return uiConfigRepository.findValue(key).map(String::trim).filter(s -> !s.isEmpty()).orElse("");
    }

    private static boolean allowedModelChar(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '.' || c == '-' || c == '_' || c == '/' || c == ':';
    }

    private static String blankOr(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }
}
