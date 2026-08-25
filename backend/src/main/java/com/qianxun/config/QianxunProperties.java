package com.qianxun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qianxun")
public class QianxunProperties {

    /**
     * Doris 中的逻辑库名（表将创建为 db.table）
     */
    private String db = "qianxun";

    private final Llm llm = new Llm();
    private final Hermes hermes = new Hermes();
    private final Claude claude = new Claude();
    private final Auth auth = new Auth();
    private final Minio minio = new Minio();
    private final Cors cors = new Cors();

    public String getDb() { return db; }
    public void setDb(String db) { this.db = db; }
    public Llm getLlm() { return llm; }
    public Hermes getHermes() { return hermes; }
    public Claude getClaude() { return claude; }
    public Auth getAuth() { return auth; }

    /** 智能体运行器（Claude Code）是否启用。 */
    public boolean isAgentRunnerEnabled() {
        return claude.isEnabled();
    }
    public Minio getMinio() { return minio; }
    public Cors getCors() { return cors; }

    /**
     * 浏览器跨域白名单。内网用 IP/主机名访问前端时必须覆盖对应 Origin，
     * 否则登录等 JSON POST 会被 Spring CORS 以 403 Invalid CORS request 拒绝。
     */
    public static class Cors {
        /**
         * 逗号分隔的 origin pattern，例如 {@code *} 或 {@code http://localhost:*,http://10.*:*}。
         * 空则回退为 {@code *}（JWT 走 Authorization 头，不依赖 Cookie）。
         */
        private String allowedOriginPatterns = "*";

        public String getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(String allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public String[] resolvedOriginPatterns() {
            if (allowedOriginPatterns == null || allowedOriginPatterns.isBlank()) {
                return new String[]{"*"};
            }
            return java.util.Arrays.stream(allowedOriginPatterns.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }
    }

    /**
     * Claude Code SDK 网关（独立容器）：聊天走 {@code POST /v1/agent/stream} NDJSON；
     * profile / 技能 / 工具集走 REST。
     */
    public static class Claude {
        private boolean enabled = false;
        /** SDK 网关根地址，例如 {@code http://claude-code:8642}。 */
        private String baseUrl = "";
        /** 网关 Bearer（可选，对应容器 CLAUDE_GATEWAY_KEY）。 */
        private String apiKey = "";
        private String chatModel = "qwen3.6-plus";
        private String permissionMode = "bypassPermissions";
        private int contextWindow = 200_000;
        /** 为 true 时在 api_server 工具集列表末尾追加 no_mcp 哨兵以禁用 MCP。 */
        private boolean appendNoMcp = false;
        /** 兼容旧配置，网关侧不再读取。 */
        private String command = "claude";
        private String dataDir = "/opt/data";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getChatModel() { return chatModel; }
        public void setChatModel(String chatModel) { this.chatModel = chatModel; }
        public String getPermissionMode() { return permissionMode; }
        public void setPermissionMode(String permissionMode) { this.permissionMode = permissionMode; }
        public int getContextWindow() { return contextWindow; }
        public void setContextWindow(int contextWindow) { this.contextWindow = contextWindow; }
        public boolean isAppendNoMcp() { return appendNoMcp; }
        public void setAppendNoMcp(boolean appendNoMcp) { this.appendNoMcp = appendNoMcp; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public String getDataDir() { return dataDir; }
        public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    }

    public static class Hermes {
        /**
         * 启用后：智能体聊天走 Dashboard {@code /api/ws}（9119）；
         * {@code base-url} 仅用于下一步建议等仍走 OpenAI 兼容网关的短请求。
         */
        private boolean enabled = false;
        private String baseUrl = "";
        private String apiKey = "";
        private String chatModel = "kimi-k2.5";

        /**
         * Hermes Dashboard 管理面根地址（默认 9119，含 {@code /api/profiles}）。
         * 留空则从 base-url 去掉末尾 /v1（仅适用于把 Dashboard 与网关混在同一 origin 的部署）。
         */
        private String adminBaseUrl = "";

        /**
         * Dashboard 用户名（bundled basic 提供者）。非空时调用管理面 API 前会先
         * {@code POST /auth/password-login}。
         */
        private String dashboardUsername = "";
        private String dashboardPassword = "";
        private String dashboardAuthProvider = "basic";

        /**
         * 为 true 时按 profile 隔离：Dashboard {@code session.create.profile}，
         * 以及建议短请求的 {@code /p/{profile}/v1/chat/completions}。
         */
        private boolean multiplexProfiles = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getAdminBaseUrl() {
            return adminBaseUrl;
        }

        public void setAdminBaseUrl(String adminBaseUrl) {
            this.adminBaseUrl = adminBaseUrl;
        }

        public String getDashboardUsername() {
            return dashboardUsername;
        }

        public void setDashboardUsername(String dashboardUsername) {
            this.dashboardUsername = dashboardUsername;
        }

        public String getDashboardPassword() {
            return dashboardPassword;
        }

        public void setDashboardPassword(String dashboardPassword) {
            this.dashboardPassword = dashboardPassword;
        }

        public String getDashboardAuthProvider() {
            return dashboardAuthProvider;
        }

        public void setDashboardAuthProvider(String dashboardAuthProvider) {
            this.dashboardAuthProvider = dashboardAuthProvider;
        }

        public boolean isMultiplexProfiles() {
            return multiplexProfiles;
        }

        public void setMultiplexProfiles(boolean multiplexProfiles) {
            this.multiplexProfiles = multiplexProfiles;
        }
    }

    public static class Llm {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private boolean mockEnabled = true;
        /**
         * 流式 chat/completions 请求的 max_tokens，避免上游默认值过小导致长文在中途被截断。
         */
        private int maxTokens = 16384;
        /**
         * 单次流式请求从发起到响应体读完的墙钟超时（秒）。
         * JDK HttpRequest.timeout 会覆盖整段读 body；智能体工具循环可能很长。
         * 0 或负数表示不限制（仅保留连接超时）。
         */
        private int streamTimeoutSeconds = 0;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public boolean isMockEnabled() { return mockEnabled; }
        public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getStreamTimeoutSeconds() { return streamTimeoutSeconds; }
        public void setStreamTimeoutSeconds(int streamTimeoutSeconds) { this.streamTimeoutSeconds = streamTimeoutSeconds; }
    }

    /**
     * 登录与 JWT：启用后除登录接口外须携带有效 Bearer Token；
     * 关闭后行为与旧版一致（仅请求头 / 默认用户）。
     */
    public static class Auth {
        private boolean enabled = true;
        private String jwtSecret = "";
        private long jwtExpirationSeconds = 86_400;
        private String defaultUsername = "admin";
        private String defaultPassword = "admin123";
        private String defaultUserId = "1";
        private String defaultDisplayName = "管理员";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getJwtExpirationSeconds() {
            return jwtExpirationSeconds;
        }

        public void setJwtExpirationSeconds(long jwtExpirationSeconds) {
            this.jwtExpirationSeconds = jwtExpirationSeconds;
        }

        public String getDefaultUsername() {
            return defaultUsername;
        }

        public void setDefaultUsername(String defaultUsername) {
            this.defaultUsername = defaultUsername;
        }

        public String getDefaultPassword() {
            return defaultPassword;
        }

        public void setDefaultPassword(String defaultPassword) {
            this.defaultPassword = defaultPassword;
        }

        public String getDefaultUserId() {
            return defaultUserId;
        }

        public void setDefaultUserId(String defaultUserId) {
            this.defaultUserId = defaultUserId;
        }

        public String getDefaultDisplayName() {
            return defaultDisplayName;
        }

        public void setDefaultDisplayName(String defaultDisplayName) {
            this.defaultDisplayName = defaultDisplayName;
        }
    }

    /**
     * 用户上传文档：按 userId 前缀隔离存放；公开下载走后端 {@code /data/files/public/{token}}。
     */
    public static class Minio {
        private String endpoint = "http://minio:9000";
        private String accessKey = "qianxun";
        private String secretKey = "qianxun-minio-dev";
        private String bucket = "qianxun";
        /**
         * 智能体（Claude Code）可访问的后端根地址，用于拼公开下载链接。
         * Docker 内应为 {@code http://qianxun-backend:8080}。
         */
        private String publicBaseUrl = "http://qianxun-backend:8080";
        private int maxFileSizeMb = 200;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
        public int getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
    }
}
