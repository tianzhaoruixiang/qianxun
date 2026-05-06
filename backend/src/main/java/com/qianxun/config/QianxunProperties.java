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
    private final DeepThink deepThink = new DeepThink();

    public String getDb() { return db; }
    public void setDb(String db) { this.db = db; }
    public Llm getLlm() { return llm; }
    public Hermes getHermes() { return hermes; }
    public DeepThink getDeepThink() { return deepThink; }

    public static class Hermes {
        /**
         * 启用后：聊天走 Hermes Agent 的 OpenAI 兼容接口（base-url/chat-model），
         * 并在流式输出前执行 NLU（非流式）意图识别与槽位抽取。
         */
        private boolean enabled = false;
        private String baseUrl = "";
        private String apiKey = "";
        private String chatModel = "kimi-k2.5";
        /**
         * 未启用 Hermes 直连时，是否与第三方 OpenAI 兼容网关配合，将 agent_skill 作为 model。
         */
        private boolean useSkillAsModel = false;

        /**
         * 启用 Hermes 且 NLU 命中场景的 agent_skill 非空时，是否用其覆盖流式请求的 model（技能路由）。
         * 默认 true：先 NLU，再按意图调用 Hermes 对应技能。
         */
        private boolean routeIntentSkill = true;

        private final Nlu nlu = new Nlu();

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

        public boolean isUseSkillAsModel() {
            return useSkillAsModel;
        }

        public void setUseSkillAsModel(boolean useSkillAsModel) {
            this.useSkillAsModel = useSkillAsModel;
        }

        public boolean isRouteIntentSkill() {
            return routeIntentSkill;
        }

        public void setRouteIntentSkill(boolean routeIntentSkill) {
            this.routeIntentSkill = routeIntentSkill;
        }

        public Nlu getNlu() {
            return nlu;
        }

        public static class Nlu {
            private boolean enabled = true;
            private String model = "";
            private double temperature = 0.0;
            private String systemPrompt = "";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model;
            }

            public double getTemperature() {
                return temperature;
            }

            public void setTemperature(double temperature) {
                this.temperature = temperature;
            }

            public String getSystemPrompt() {
                return systemPrompt;
            }

            public void setSystemPrompt(String systemPrompt) {
                this.systemPrompt = systemPrompt;
            }
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
         * 单次流式请求从发起到响应体读完的最长等待（秒），含慢速长文生成；过短会导致连接被 JDK HttpClient 提前掐断。
         */
        private int streamTimeoutSeconds = 3600;

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
     * 深度思考模式配置。
     * 复用 hermes-agent，不引入新的模型配置；
     * 仅通过 system-prompt 注入额外 CoT 指令，留空则使用内置模板。
     */
    public static class DeepThink {
        /** 自定义深度思考前置系统提示词，留空使用内置 CoT 模板 */
        private String systemPrompt = "";

        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    }
}
