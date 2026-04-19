package com.qianxun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ChatActivityLog;
import com.qianxun.domain.ChatMessage;
import com.qianxun.domain.IntentScenario;
import com.qianxun.llm.OpenAiCompatibleStreamClient;
import com.qianxun.nlu.IntentSlotUnderstanding;
import com.qianxun.nlu.QianXunIntentSlotUnderstandingService;
import com.qianxun.nlu.PromptTemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QianXunChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(QianXunChatStreamService.class);

    /** 内置深度思考 CoT 系统提示词（hermes-agent 不支持 <think> 标签时也生效） */
    private static final String DEFAULT_DEEP_SYSTEM_PROMPT = """
            你当前处于【深度思考模式】。
            在正式作答前，请先在 <think> 标签中进行充分的内部推理：
            - 拆解问题的各个维度与核心诉求
            - 评估可能的分析路径与答案假设
            - 识别关键证据、潜在风险与不确定性
            - 综合以上推理得出结论
            
            格式要求：
            <think>
            （此处写内部推理过程，用户可以展开查看但不作为正式回复）
            </think>
            
            （此处写正式、详细、有条理的回答）
            """;

    private final QianXunChatSessionService sessionService;
    private final OpenAiCompatibleStreamClient openAiClient;
    private final QianXunIntentSlotUnderstandingService intentSlotUnderstandingService;
    private final QianXunIntentScenarioService intentScenarioService;
    private final QianXunActivityLogService activityLogService;
    private final ObjectMapper objectMapper;
    private final QianxunProperties properties;

    public QianXunChatStreamService(
            QianXunChatSessionService sessionService,
            OpenAiCompatibleStreamClient openAiClient,
            QianXunIntentSlotUnderstandingService intentSlotUnderstandingService,
            QianXunIntentScenarioService intentScenarioService,
            QianXunActivityLogService activityLogService,
            ObjectMapper objectMapper,
            QianxunProperties properties
    ) {
        this.sessionService = sessionService;
        this.openAiClient = openAiClient;
        this.intentSlotUnderstandingService = intentSlotUnderstandingService;
        this.intentScenarioService = intentScenarioService;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void streamAnswer(
            String userId, String sessionId,
            String userContent, boolean deepMode,
            SseEmitter emitter
    ) {
        long totalStart = System.currentTimeMillis();
        String logId = UUID.randomUUID().toString().replace("-", "");
        ChatActivityLog.Builder logBuilder = ChatActivityLog.builder()
                .id(logId)
                .userId(userId)
                .sessionId(sessionId)
                .userContent(userContent)
                .thinkingMode(deepMode ? ChatMessage.MODE_DEEP : ChatMessage.MODE_QUICK)
                .createdAt(Instant.now());

        try {
            ChatMessage userMsg = sessionService.appendUserMessage(sessionId, userContent);
            logBuilder.userMessageId(userMsg.id());

            boolean useMock = shouldUseMock();
            if (!useMock) {
                ChatEndpoint preview = resolveChatEndpoint(null);
                if (preview.baseUrl().isBlank()) {
                    throw new IllegalStateException("未配置 LLM/Hermes 的 base-url");
                }
                if (!properties.getHermes().isEnabled() && preview.apiKey().trim().isEmpty()) {
                    throw new IllegalStateException("缺少 OPENAI_API_KEY（或 qianxun.llm.api-key）");
                }
            }

            List<ChatMessage> history = sessionService.loadHistoryForLlm(sessionId);
            List<Map<String, String>> llmMessages = toOpenAiMessages(history);

            // ── NLU 阶段 ───────────────────────────────────────────────────────
            long nluStart = System.currentTimeMillis();
            IntentSlotUnderstanding understanding = maybeUnderstand(userContent, useMock);
            long nluDuration = System.currentTimeMillis() - nluStart;
            logBuilder.nluDurationMs(nluDuration);

            IntentScenario scenario = understanding == null ? null : understanding.scenario();
            if (understanding != null) {
                fillNluFields(logBuilder, understanding);
                sendAnalysis(emitter, understanding);
                // 如有 agentSkill，推送一个代理步骤事件让前端显示
                if (understanding.agentSkill() != null && !understanding.agentSkill().isBlank()) {
                    sendEvent(emitter, "agent_step", Map.of(
                            "type", "agent_skill",
                            "label", understanding.agentSkill(),
                            "detail", understanding.scenarioName() == null ? "" : understanding.scenarioName()
                    ));
                }
                llmMessages = augmentWithScenarioAndNlu(llmMessages, understanding);
            }

            // ── 深度思考模式：注入 CoT 系统提示词 ──────────────────────────────
            if (deepMode) {
                llmMessages = injectDeepThinkPrompt(llmMessages);
                sendEvent(emitter, "think_start", Map.of());
            }

            // ── LLM 阶段 ───────────────────────────────────────────────────────
            ChatEndpoint endpoint = resolveChatEndpoint(scenario);
            logBuilder.llmEndpoint(endpoint.baseUrl()).llmModel(endpoint.model());

            StringBuilder responseText  = new StringBuilder(); // 正式回复
            StringBuilder thinkText     = new StringBuilder(); // 推理内容

            ThinkBlockStreamParser thinkParser = deepMode ? new ThinkBlockStreamParser() : null;

            long llmStart = System.currentTimeMillis();
            if (useMock) {
                logBuilder.status(ChatActivityLog.STATUS_MOCK);
                logBuilder.llmRequestJson(buildMockRequestNote(userContent));
                openAiClient.streamMockReply(userContent, token -> {
                    handleToken(emitter, token, deepMode, thinkParser, responseText, thinkText);
                });
            } else {
                String requestJson = buildLlmRequestJson(endpoint.model(), llmMessages);
                logBuilder.llmRequestJson(requestJson);
                openAiClient.streamChatCompletions(
                        endpoint.baseUrl(), endpoint.apiKey(), endpoint.model(), llmMessages,
                        token -> handleToken(emitter, token, deepMode, thinkParser, responseText, thinkText),
                        toolCall -> sendToolCallEvent(emitter, toolCall)
                );
            }

            // 冲洗解析器缓冲
            if (thinkParser != null) {
                for (ThinkBlockStreamParser.Chunk chunk : thinkParser.flush()) {
                    routeChunk(emitter, chunk, responseText, thinkText);
                }
            }

            long llmDuration = System.currentTimeMillis() - llmStart;
            logBuilder.llmDurationMs(llmDuration);
            logBuilder.llmResponseText(responseText.toString());

            String thinkContent = thinkText.isEmpty() ? null : thinkText.toString();
            logBuilder.thinkContent(thinkContent);

            if (deepMode) {
                sendEvent(emitter, "think_end", Map.of("thinkContent", thinkContent == null ? "" : thinkContent));
            }

            // ── 保存消息 & 活动日志 ──────────────────────────────────────────────
            var saved = sessionService.appendAssistantMessage(
                    sessionId, responseText.toString(),
                    deepMode ? ChatMessage.MODE_DEEP : ChatMessage.MODE_QUICK,
                    thinkContent
            );
            logBuilder.assistantMessageId(saved.id())
                      .totalDurationMs(System.currentTimeMillis() - totalStart);
            activityLogService.saveLog(logBuilder.build());

            sendEvent(emitter, "done", Map.of(
                    "assistantMessageId", saved.id(),
                    "sessionId", sessionId
            ));
            emitter.complete();

        } catch (Exception ex) {
            log.warn("流式问答失败: {}", ex.toString());
            activityLogService.saveLog(
                    logBuilder.status(ChatActivityLog.STATUS_ERROR)
                              .errorMessage(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                              .totalDurationMs(System.currentTimeMillis() - totalStart)
                              .build()
            );
            try {
                sendEvent(emitter, "error", Map.of("message", ex.getMessage() == null ? "unknown" : ex.getMessage()));
            } catch (Exception ignored) {}
            emitter.completeWithError(ex);
        }
    }

    // ── Token 路由 ─────────────────────────────────────────────────────────────

    private void handleToken(
            SseEmitter emitter, String token, boolean deepMode,
            ThinkBlockStreamParser parser,
            StringBuilder responseText, StringBuilder thinkText
    ) {
        if (!deepMode || parser == null) {
            responseText.append(token);
            sendTokenEvent(emitter, token, false);
            return;
        }
        for (ThinkBlockStreamParser.Chunk chunk : parser.feed(token)) {
            routeChunk(emitter, chunk, responseText, thinkText);
        }
    }

    private void routeChunk(
            SseEmitter emitter, ThinkBlockStreamParser.Chunk chunk,
            StringBuilder responseText, StringBuilder thinkText
    ) {
        if (chunk.type() == ThinkBlockStreamParser.ChunkType.THINK) {
            thinkText.append(chunk.text());
            sendTokenEvent(emitter, chunk.text(), true);
        } else {
            responseText.append(chunk.text());
            sendTokenEvent(emitter, chunk.text(), false);
        }
    }

    private static void sendTokenEvent(SseEmitter emitter, String text, boolean isThink) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", text);
            if (isThink) data.put("think", true);
            emitter.send(SseEmitter.event().name(isThink ? "think_token" : "token").data(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sendToolCallEvent(SseEmitter emitter, OpenAiCompatibleStreamClient.ToolCallEvent tc) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("toolCallId", tc.toolCallId());
            data.put("toolName", tc.functionName());
            data.put("args", tc.argsChunk());
            emitter.send(SseEmitter.event().name("tool_call").data(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── 深度思考 CoT 注入 ───────────────────────────────────────────────────────

    private List<Map<String, String>> injectDeepThinkPrompt(List<Map<String, String>> messages) {
        String prompt = properties.getDeepThink().getSystemPrompt();
        if (prompt == null || prompt.isBlank()) {
            prompt = DEFAULT_DEEP_SYSTEM_PROMPT;
        }
        List<Map<String, String>> result = new ArrayList<>();
        // CoT 提示词作为第一条 system 消息（优先级最高）
        LinkedHashMap<String, String> cot = new LinkedHashMap<>();
        cot.put("role", "system");
        cot.put("content", prompt);
        result.add(cot);
        result.addAll(messages);
        return result;
    }

    // ── NLU ────────────────────────────────────────────────────────────────────

    private void fillNluFields(ChatActivityLog.Builder b, IntentSlotUnderstanding u) {
        b.nluIntent(u.intent())
                .nluScenarioCode(u.scenarioCode())
                .nluScenarioName(u.scenarioName())
                .nluAgentSkill(u.agentSkill() == null ? "" : u.agentSkill())
                .nluConfidence(u.confidence())
                .nluReasoning(u.reasoning())
                .nluRawResponse(u.rawModelText());
        try {
            b.nluSlots(objectMapper.writeValueAsString(u.safeSlots()));
            b.nluMissingSlots(objectMapper.writeValueAsString(u.safeMissingRequiredSlots()));
        } catch (Exception ex) {
            log.debug("NLU 槽位序列化失败（忽略）: {}", ex.toString());
        }
    }

    private void sendAnalysis(SseEmitter emitter, IntentSlotUnderstanding u) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", u.intent());
        payload.put("scenarioCode", u.scenarioCode());
        payload.put("scenarioName", u.scenarioName());
        payload.put("agentSkill", u.agentSkill() == null ? "" : u.agentSkill());
        payload.put("slots", u.safeSlots());
        payload.put("missingRequiredSlots", u.safeMissingRequiredSlots());
        payload.put("confidence", u.confidence());
        payload.put("reasoning", u.reasoning() == null ? "" : u.reasoning());
        sendEvent(emitter, "analysis", payload);
    }

    private List<Map<String, String>> augmentWithScenarioAndNlu(
            List<Map<String, String>> history, IntentSlotUnderstanding u
    ) {
        try {
            List<Map<String, String>> out = new ArrayList<>();
            IntentScenario scenario = u.scenario();
            if (scenario != null
                    && scenario.promptTemplate() != null
                    && !scenario.promptTemplate().isBlank()) {
                String rendered = PromptTemplateRenderer.render(scenario.promptTemplate(), u.safeSlots());
                if (!rendered.isBlank()) {
                    LinkedHashMap<String, String> s = new LinkedHashMap<>();
                    s.put("role", "system");
                    s.put("content", rendered);
                    out.add(s);
                }
            }
            String slotsJson = objectMapper.writeValueAsString(u.safeSlots());
            String missing = u.safeMissingRequiredSlots().isEmpty()
                    ? "无" : String.join(", ", u.safeMissingRequiredSlots());
            String sysText = """
                    【千寻·NLU】
                    场景：%s（code=%s, agent_skill=%s）
                    置信度：%.2f
                    槽位(JSON)：%s
                    缺失必填槽位：%s

                    请遵循上述场景定位与槽位回答；如关键槽位缺失，先简洁地向用户确认再继续。
                    """.formatted(
                    nullSafe(u.scenarioName()), nullSafe(u.scenarioCode()),
                    nullSafe(u.agentSkill()), u.confidence(), slotsJson, missing);
            LinkedHashMap<String, String> sysNlu = new LinkedHashMap<>();
            sysNlu.put("role", "system");
            sysNlu.put("content", sysText);
            out.add(sysNlu);
            out.addAll(history);
            return out;
        } catch (Exception ex) {
            log.warn("NLU/场景上下文注入失败，将忽略 system 前缀: {}", ex.toString());
            return history;
        }
    }

    private IntentSlotUnderstanding maybeUnderstand(String userContent, boolean useMock) throws Exception {
        QianxunProperties.Hermes hermes = properties.getHermes();
        if (!hermes.isEnabled() || !hermes.getNlu().isEnabled()) return null;
        List<IntentScenario> scenarios = intentScenarioService.listEnabled();
        String baseUrl  = trim(hermes.getBaseUrl());
        String apiKey   = coalesce(hermes.getApiKey(), properties.getLlm().getApiKey());
        String nluModel = resolveNluModel(hermes);
        if (!useMock && !baseUrl.isBlank()) {
            return intentSlotUnderstandingService.understand(
                    userContent, scenarios, baseUrl, apiKey, nluModel,
                    hermes.getNlu().getTemperature(), hermes.getNlu().getSystemPrompt()
            );
        }
        return intentSlotUnderstandingService.mockUnderstand(userContent, scenarios);
    }

    // ── 工具方法 ───────────────────────────────────────────────────────────────

    private String buildLlmRequestJson(String model, List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", true);
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            return "{\"error\":\"serialize failed\"}";
        }
    }

    private static String buildMockRequestNote(String userContent) {
        String safe = userContent.substring(0, Math.min(userContent.length(), 200));
        return "{\"mock\":true,\"user_content\":" + safe.replace("\"", "\\\"") + "}";
    }

    private boolean shouldUseMock() {
        QianxunProperties.Hermes hermes = properties.getHermes();
        if (hermes.isEnabled()) return trim(hermes.getBaseUrl()).isEmpty();
        QianxunProperties.Llm llm = properties.getLlm();
        String key = llm.getApiKey() == null ? "" : llm.getApiKey().trim();
        if (!key.isEmpty()) return false;
        return llm.isMockEnabled();
    }

    private ChatEndpoint resolveChatEndpoint(IntentScenario scenario) {
        QianxunProperties.Hermes hermes = properties.getHermes();
        ChatEndpoint base;
        if (hermes.isEnabled()) {
            base = new ChatEndpoint(
                    trim(hermes.getBaseUrl()),
                    coalesce(hermes.getApiKey(), properties.getLlm().getApiKey()),
                    blankOrDefault(hermes.getChatModel(), properties.getLlm().getModel())
            );
        } else {
            QianxunProperties.Llm llm = properties.getLlm();
            base = new ChatEndpoint(trim(llm.getBaseUrl()), llm.getApiKey(), llm.getModel());
        }
        boolean allowSkillAsModel = !hermes.isEnabled() || hermes.isUseSkillAsModel();
        if (allowSkillAsModel && scenario != null
                && scenario.agentSkill() != null && !scenario.agentSkill().isBlank()) {
            return new ChatEndpoint(base.baseUrl(), base.apiKey(), scenario.agentSkill().trim());
        }
        return base;
    }

    private String resolveNluModel(QianxunProperties.Hermes hermes) {
        String m = hermes.getNlu().getModel();
        if (m != null && !m.trim().isEmpty()) return m.trim();
        String chat = hermes.getChatModel();
        if (chat != null && !chat.trim().isEmpty()) return chat.trim();
        return properties.getLlm().getModel();
    }

    private static List<Map<String, String>> toOpenAiMessages(List<ChatMessage> history) {
        List<Map<String, String>> out = new ArrayList<>();
        for (ChatMessage m : history) {
            if (!"user".equals(m.role()) && !"assistant".equals(m.role()) && !"system".equals(m.role())) continue;
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            row.put("role", m.role());
            row.put("content", m.content());
            out.add(row);
        }
        return out;
    }

    private static String coalesce(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        return b == null ? "" : b.trim();
    }

    private static String blankOrDefault(String v, String d) {
        if (v == null || v.trim().isEmpty()) return d == null ? "" : d.trim();
        return v.trim();
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }
    private static String nullSafe(String v) { return v == null ? "" : v; }

    private record ChatEndpoint(String baseUrl, String apiKey, String model) {}
}
