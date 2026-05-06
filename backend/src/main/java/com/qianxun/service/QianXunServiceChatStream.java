package com.qianxun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.chat.EntityBlockParser;
import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ChatActivityLog;
import com.qianxun.domain.ChatMessage;
import com.qianxun.domain.IntentScenario;
import com.qianxun.llm.OpenAiCompatibleStreamClient;
import com.qianxun.nlu.IntentSlotUnderstanding;
import com.qianxun.nlu.QianXunServiceIntentSlotUnderstanding;
import com.qianxun.nlu.PromptTemplateRenderer;
import com.qianxun.repo.DatasetRegistryRepository;
import com.qianxun.repo.DataFileRepository;
import com.qianxun.repo.ModelRegistryRepository;
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
public class QianXunServiceChatStream {

    private static final Logger log = LoggerFactory.getLogger(QianXunServiceChatStream.class);

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

    /**
     * 要求模型在正文末尾输出机器可解析的实体块（前端不再依赖关键词启发式）。
     */
    private static final String ENTITY_EXPORT_SYSTEM_PROMPT = """
            【千寻 · 实体结构化输出】
            在用户可见的正文全部写完后，请在全文最后追加一段且仅一段代码块用于实体列表：
            - 围栏第一行必须为（区分大小写）：```qianxun-entities
            - 围栏内为 JSON 数组；每个元素为对象，字段如下：
              - name（必填）：实体中文或常用显示名
              - category（必填）：只能是 person、time、location、org、event、thing 之一
              - type（可选）：子类型或角色，如「董事长」「官方声明」
              - nameEn（可选）：英文名或拉丁转写
              - description（可选）：不超过 80 字的简短说明
            - 仅收录本回答中有明确依据的重要实体；没有则输出空数组 []。
            - 除上述代码块外，不要用其它方式描述该 JSON；代码块紧接正文末尾，前面空一行。

            示例（格式示意，勿照抄内容）：
            ```qianxun-entities
            [{"name":"示例科技","category":"org","type":"企业","description":"示例"}]
            ```
            """;

    private final QianXunServiceChatSession sessionService;
    private final OpenAiCompatibleStreamClient openAiClient;
    private final QianXunServiceIntentSlotUnderstanding intentSlotUnderstandingService;
    private final QianXunServiceIntentScenario intentScenarioService;
    private final QianXunServiceActivityLog activityLogService;
    private final ModelRegistryRepository modelRegistryRepository;
    private final DatasetRegistryRepository datasetRegistryRepository;
    private final DataFileRepository dataFileRepository;
    private final ObjectMapper objectMapper;
    private final QianxunProperties properties;

    public QianXunServiceChatStream(
            QianXunServiceChatSession sessionService,
            OpenAiCompatibleStreamClient openAiClient,
            QianXunServiceIntentSlotUnderstanding intentSlotUnderstandingService,
            QianXunServiceIntentScenario intentScenarioService,
            QianXunServiceActivityLog activityLogService,
            ModelRegistryRepository modelRegistryRepository,
            DatasetRegistryRepository datasetRegistryRepository,
            DataFileRepository dataFileRepository,
            ObjectMapper objectMapper,
            QianxunProperties properties
    ) {
        this.sessionService = sessionService;
        this.openAiClient = openAiClient;
        this.intentSlotUnderstandingService = intentSlotUnderstandingService;
        this.intentScenarioService = intentScenarioService;
        this.activityLogService = activityLogService;
        this.modelRegistryRepository = modelRegistryRepository;
        this.datasetRegistryRepository = datasetRegistryRepository;
        this.dataFileRepository = dataFileRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 置信度低于此阈值时触发意图澄清，不直接调 LLM */
    private static final double CLARIFICATION_THRESHOLD = 0.50;

    public void streamAnswer(
            String userId, String sessionId,
            String userContent, boolean deepMode,
            String confirmedScenarioCode,
            String modelCode,
            String datasetCode,
            List<String> selectedFileIds,
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

        StreamContext ctx = new StreamContext(emitter, logBuilder, deepMode);

        try {
            ChatMessage userMsg = sessionService.appendUserMessage(sessionId, userContent);
            logBuilder.userMessageId(userMsg.id());

            boolean useMock = validateConfig();

            List<ChatMessage> history = sessionService.loadHistoryForLlm(sessionId);
            List<Map<String, String>> llmMessages = toOpenAiMessages(history);
            boolean hasPriorTurns = history.size() > 1;

            // ── NLU 阶段 ───────────────────────────────────────────────────────
            IntentSlotUnderstanding understanding = runNluPhase(userContent, history, confirmedScenarioCode, useMock);
            if (understanding != null) {
                handleNluResult(ctx, understanding, userContent, llmMessages, hasPriorTurns, confirmedScenarioCode);
            }

            llmMessages = injectEntityExportInstruction(llmMessages);
            if (deepMode) {
                llmMessages = injectDeepThinkPrompt(llmMessages);
                sendEvent(emitter, "think_start", Map.of());
            }
            llmMessages = injectDatasetContext(llmMessages, datasetCode);
            llmMessages = injectSelectedFilesContext(llmMessages, selectedFileIds);

            // ── LLM 阶段 ───────────────────────────────────────────────────────
            callLlmStream(ctx, llmMessages, useMock, userContent, modelCode);

            // ── 后处理 ──────────────────────────────────────────────────────────
            parseAndSendEntities(ctx);
            saveAndComplete(ctx);

        } catch (Exception ex) {
            handleStreamError(ctx, totalStart, ex);
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
            if (isThink) { data.put("think", true); }
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

    private List<Map<String, String>> injectEntityExportInstruction(List<Map<String, String>> messages) {
        List<Map<String, String>> out = new ArrayList<>();
        LinkedHashMap<String, String> s = new LinkedHashMap<>();
        s.put("role", "system");
        s.put("content", ENTITY_EXPORT_SYSTEM_PROMPT);
        out.add(s);
        out.addAll(messages);
        return out;
    }

    private List<Map<String, String>> injectDatasetContext(List<Map<String, String>> messages, String datasetCode) {
        if (datasetCode == null || datasetCode.isBlank()) {
            return messages;
        }
        var datasetOpt = datasetRegistryRepository.findByCode(datasetCode.trim());
        if (datasetOpt.isEmpty() || !datasetOpt.get().enabled()) {
            return messages;
        }
        var d = datasetOpt.get();
        String datasetPrompt = """
                当前会话已选择数据集，请优先参考其语义范围回答：
                - 数据集编码：%s
                - 数据集名称：%s
                - 描述：%s
                - 来源类型：%s
                - 来源引用：%s
                - 文档数量：%d
                若问题超出该数据集范围，请明确提示并给出可扩展建议。
                """.formatted(
                nullSafe(d.code()), nullSafe(d.name()), nullSafe(d.description()),
                nullSafe(d.sourceType()), nullSafe(d.sourceRef()), d.docCount()
        );
        List<Map<String, String>> out = new ArrayList<>(messages.size() + 1);
        LinkedHashMap<String, String> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", datasetPrompt);
        out.add(sys);
        out.addAll(messages);
        return out;
    }

    private List<Map<String, String>> injectSelectedFilesContext(List<Map<String, String>> messages, List<String> selectedFileIds) {
        if (selectedFileIds == null || selectedFileIds.isEmpty()) {
            return messages;
        }
        List<String> ids = selectedFileIds.stream().filter(v -> v != null && !v.isBlank()).limit(10).toList();
        if (ids.isEmpty()) {
            return messages;
        }
        var files = dataFileRepository.findByIds(ids);
        if (files.isEmpty()) {
            return messages;
        }
        StringBuilder sb = new StringBuilder("当前会话已选择以下中间数据文件，请优先围绕这些文件回答：\n");
        for (int i = 0; i < files.size(); i++) {
            var f = files.get(i);
            sb.append(i + 1).append(". [").append(nullSafe(f.kind())).append("] ")
                    .append(nullSafe(f.name())).append(" (").append(nullSafe(f.displayDate())).append(")\n");
            if (f.detailText() != null && !f.detailText().isBlank()) {
                String preview = f.detailText().replaceAll("\\s+", " ").trim();
                if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
                sb.append("   摘要：").append(preview).append("\n");
            }
        }
        List<Map<String, String>> out = new ArrayList<>(messages.size() + 1);
        LinkedHashMap<String, String> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", sb.toString());
        out.add(sys);
        out.addAll(messages);
        return out;
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

    private Map<String, Object> buildAnalysisPayload(IntentSlotUnderstanding u) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", u.intent());
        payload.put("scenarioCode", u.scenarioCode());
        payload.put("scenarioName", u.scenarioName());
        payload.put("agentSkill", u.agentSkill() == null ? "" : u.agentSkill());
        payload.put("slots", u.safeSlots());
        payload.put("missingRequiredSlots", u.safeMissingRequiredSlots());
        payload.put("confidence", u.confidence());
        payload.put("reasoning", u.reasoning() == null ? "" : u.reasoning());
        return payload;
    }

    private void sendAnalysis(SseEmitter emitter, IntentSlotUnderstanding u) {
        sendEvent(emitter, "analysis", buildAnalysisPayload(u));
    }

    private List<Map<String, String>> augmentWithScenarioAndNlu(
            List<Map<String, String>> history, IntentSlotUnderstanding u, boolean hasPriorTurns
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
            String multiTurnHint = hasPriorTurns
                    ? "对话含多轮上下文：请结合 messages 中的历史消息做指代消解；用户本轮若仅为补充说明，须与首轮问题中的实体/事件合并理解，不得替换为泛化描述。\n"
                    : "";
            String sysText = """
                    【千寻·NLU】
                    场景：%s（code=%s, agent_skill=%s）
                    置信度：%.2f
                    槽位(JSON)：%s
                    缺失必填槽位：%s

                    %s请遵循上述场景定位与槽位回答；如关键槽位缺失，先简洁地向用户确认再继续。
                    """.formatted(
                    nullSafe(u.scenarioName()), nullSafe(u.scenarioCode()),
                    nullSafe(u.agentSkill()), u.confidence(), slotsJson, missing, multiTurnHint);
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

    private IntentSlotUnderstanding maybeUnderstand(
            String userContent, List<ChatMessage> sessionHistory, boolean useMock
    ) throws Exception {
        QianxunProperties.Hermes hermes = properties.getHermes();
        if (!hermes.isEnabled() || !hermes.getNlu().isEnabled()) return null;
        List<IntentScenario> scenarios = intentScenarioService.listEnabled();
        String baseUrl  = trim(hermes.getBaseUrl());
        String apiKey   = coalesce(hermes.getApiKey(), properties.getLlm().getApiKey());
        String nluModel = resolveNluModel(hermes);
        String priorDialog = buildNluPriorDialogExcerpt(sessionHistory);
        if (!useMock && !baseUrl.isBlank()) {
            return intentSlotUnderstandingService.understand(
                    userContent, priorDialog, scenarios, baseUrl, apiKey, nluModel,
                    hermes.getNlu().getTemperature(), hermes.getNlu().getSystemPrompt()
            );
        }
        return intentSlotUnderstandingService.mockUnderstand(userContent, priorDialog, scenarios);
    }

    /** 供 NLU 使用的上文摘录：不含本轮用户句（history 最后一条须为当前用户消息）。 */
    private static String buildNluPriorDialogExcerpt(List<ChatMessage> history) {
        if (history == null || history.size() < 2) {
            return "";
        }
        List<ChatMessage> prior = history.subList(0, history.size() - 1);
        int maxMsgs = 12;
        int start = Math.max(0, prior.size() - maxMsgs);
        int perMsgCap = 3200;
        final StringBuilder sb = new StringBuilder();
        sb.append("【本会话当前输入之前的对话摘录】\n");
        for (int i = start; i < prior.size(); i++) {
            ChatMessage m = prior.get(i);
            if (!"user".equals(m.role()) && !"assistant".equals(m.role())) {
                continue;
            }
            String label = "user".equals(m.role()) ? "用户" : "助手";
            String body = m.content() == null ? "" : m.content();
            if (body.length() > perMsgCap) {
                body = body.substring(0, perMsgCap) + "…";
            }
            sb.append(label).append("：\n").append(body.strip()).append("\n\n");
        }
        return sb.toString().strip();
    }

    // ── 意图澄清 ───────────────────────────────────────────────────────────────

    /**
     * 发送 clarification 事件：包含候选场景列表，供前端渲染选择按钮。
     * 候选场景 = 所有启用的非 general 场景 + general（兜底）。
     */
    private void sendClarification(
            SseEmitter emitter,
            IntentSlotUnderstanding understanding,
            String originalQuery,
            List<IntentScenario> allScenarios
    ) {
        // 构建候选选项：先展示识别出的场景（置顶），再其他非 general 场景，最后是 general
        List<Map<String, Object>> options = new ArrayList<>();
        String detected = understanding.scenarioCode();
        for (IntentScenario s : allScenarios) {
            if (s.isGeneral()) { continue; }
            if (s.code().equals(detected)) {
                Map<String, Object> opt = new LinkedHashMap<>();
                opt.put("code", s.code());
                opt.put("name", s.name());
                opt.put("description", s.description() == null ? "" : s.description());
                opt.put("detected", true);
                options.add(0, opt);
            } else {
                Map<String, Object> opt = new LinkedHashMap<>();
                opt.put("code", s.code());
                opt.put("name", s.name());
                opt.put("description", s.description() == null ? "" : s.description());
                opt.put("detected", false);
                options.add(opt);
            }
        }
        // 添加 general 兜底选项
        Map<String, Object> general = new LinkedHashMap<>();
        general.put("code", IntentScenario.GENERAL_CODE);
        general.put("name", "通用问答");
        general.put("description", "直接回答问题，不限调研方向");
        general.put("detected", false);
        options.add(general);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", "您的问题可以从多个方向解读，请选择您最想了解的方向：");
        payload.put("originalQuery", originalQuery);
        payload.put("confidence", understanding.confidence());
        payload.put("options", options);
        sendEvent(emitter, "clarification", payload);
    }

    /**
     * 用户确认意图后，跳过 NLU 直接从场景列表构建 understanding（置信度设为 1.0）。
     * 提供空 slots，让 LLM 自行从对话中提取；prompt template 的占位符会以默认值填充。
     */
    private IntentSlotUnderstanding buildFromConfirmedScenario(
            String code, List<IntentScenario> allScenarios
    ) {
        IntentScenario matched = allScenarios.stream()
                .filter(s -> s.code().equalsIgnoreCase(code.trim()))
                .findFirst().orElse(null);
        if (matched == null) {
            return new IntentSlotUnderstanding(
                    IntentScenario.GENERAL_CODE, IntentScenario.GENERAL_CODE,
                    "通用问答", "", Map.of(), List.of(), 1.0,
                    "用户已确认意图（未匹配，回退通用）", "", null
            );
        }
        return new IntentSlotUnderstanding(
                matched.code(), matched.code(), matched.name(),
                matched.agentSkill() == null ? "" : matched.agentSkill(),
                Map.of(), List.of(), 1.0,
                "用户已确认意图: " + matched.code(), "", matched
        );
    }

    // ── 工具方法 ───────────────────────────────────────────────────────────────

    private String buildLlmRequestJson(String model, List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("max_tokens", Math.max(256, properties.getLlm().getMaxTokens()));
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

    private ChatEndpoint resolveChatEndpoint(IntentScenario scenario, String selectedModelCode) {
        if (selectedModelCode != null && !selectedModelCode.isBlank()) {
            var selected = modelRegistryRepository.findByCode(selectedModelCode.trim());
            if (selected.isPresent() && selected.get().enabled()) {
                var m = selected.get();
                String baseUrl = m.baseUrl() == null ? "" : m.baseUrl().trim();
                // registry.code 即上游 model 字段，便于“配置选择”
                return new ChatEndpoint(baseUrl, resolveApiKeyByProvider(m.provider()), m.code());
            }
        }
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

    private String resolveApiKeyByProvider(String provider) {
        String p = provider == null ? "" : provider.trim().toLowerCase();
        if ("kimi-coding".equals(p)) {
            return coalesce(System.getenv("KIMI_API_KEY"), properties.getLlm().getApiKey());
        }
        return properties.getLlm().getApiKey();
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
            if (!"user".equals(m.role()) && !"assistant".equals(m.role()) && !"system".equals(m.role())) { continue; }
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            row.put("role", m.role());
            // 对深度思考消息，将 think_content 还原为 <think>...</think> 前缀，
            // 让 hermes-agent 在后续轮次中能看到完整的推理过程。
            String content = m.content();
            if ("assistant".equals(m.role())
                    && ChatMessage.MODE_DEEP.equals(m.thinkingMode())
                    && m.thinkContent() != null && !m.thinkContent().isBlank()) {
                content = "<think>\n" + m.thinkContent().strip() + "\n</think>\n" + content;
            }
            row.put("content", content);
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

    private static class StreamContext {
        private final SseEmitter emitter;
        private final ChatActivityLog.Builder logBuilder;
        private final boolean deepMode;
        private final StringBuilder responseText = new StringBuilder();
        private final StringBuilder thinkText    = new StringBuilder();
        private IntentScenario scenario;
        /** NLU 阶段的意图分析 JSON，写入 assistant 消息 */
        private String intentAnalysisJson;

        StreamContext(SseEmitter emitter, ChatActivityLog.Builder logBuilder, boolean deepMode) {
            this.emitter = emitter;
            this.logBuilder = logBuilder;
            this.deepMode = deepMode;
        }
        SseEmitter emitter()    { return emitter; }
        ChatActivityLog.Builder logBuilder() { return logBuilder; }
        boolean deepMode()      { return deepMode; }
        IntentScenario scenario()    { return scenario; }
        void setScenario(IntentScenario s) { this.scenario = s; }
        String intentAnalysisJson() { return intentAnalysisJson; }
        void setIntentAnalysisJson(String json) { this.intentAnalysisJson = json; }
    }

    // ── 各阶段拆分方法 ─────────────────────────────────────────────────────────

    private boolean validateConfig() {
        boolean useMock = shouldUseMock();
        if (!useMock) {
            ChatEndpoint preview = resolveChatEndpoint(null, null);
            if (preview.baseUrl().isBlank()) {
                throw new IllegalStateException("未配置 LLM/Hermes 的 base-url");
            }
            if (!properties.getHermes().isEnabled() && preview.apiKey().trim().isEmpty()) {
                throw new IllegalStateException("缺少 OPENAI_API_KEY（或 qianxun.llm.api-key）");
            }
        }
        return useMock;
    }

    private IntentSlotUnderstanding runNluPhase(
            String userContent, List<ChatMessage> history,
            String confirmedScenarioCode, boolean useMock
    ) throws Exception {
        IntentSlotUnderstanding result;
        if (confirmedScenarioCode != null && !confirmedScenarioCode.isBlank()) {
            result = buildFromConfirmedScenario(confirmedScenarioCode, intentScenarioService.listEnabled());
            log.info("意图已由用户确认: code={}", confirmedScenarioCode);
        } else {
            result = maybeUnderstand(userContent, history, useMock);
        }
        return result;
    }

    private void handleNluResult(
            StreamContext ctx,
            IntentSlotUnderstanding understanding,
            String userContent,
            List<Map<String, String>> llmMessages,
            boolean hasPriorTurns,
            String confirmedScenarioCode
    ) {
        IntentScenario scenario = understanding.scenario();
        fillNluFields(ctx.logBuilder(), understanding);
        sendAnalysis(ctx.emitter(), understanding);
        try {
            ctx.setIntentAnalysisJson(objectMapper.writeValueAsString(buildAnalysisPayload(understanding)));
        } catch (Exception ex) {
            log.debug("intent analysis JSON 序列化失败（忽略）: {}", ex.toString());
        }
        if (understanding.agentSkill() != null && !understanding.agentSkill().isBlank()) {
            sendEvent(ctx.emitter(), "agent_step", Map.of(
                    "type", "agent_skill",
                    "label", understanding.agentSkill(),
                    "detail", understanding.scenarioName() == null ? "" : understanding.scenarioName()
            ));
        }
        // 未命中场景（general）或置信度低于阈值时，直接使用 general 场景回答，不触发澄清
        List<Map<String, String>> augmented = augmentWithScenarioAndNlu(llmMessages, understanding, hasPriorTurns);
        llmMessages.clear();
        llmMessages.addAll(augmented);
        ctx.setScenario(scenario);
    }

    private void handleLowConfidence(StreamContext ctx, IntentSlotUnderstanding understanding, String userContent) {
        List<IntentScenario> allScenarios = intentScenarioService.listEnabled();
        sendClarification(ctx.emitter(), understanding, userContent, allScenarios);
        String msg = "🤔 您的问题意图尚不明确（置信度 "
                + Math.round(understanding.confidence() * 100) + "%），请选择您想调研的方向。";
        String sessionId = ctx.logBuilder().build().sessionId();
        var saved = sessionService.appendAssistantMessage(
                sessionId, msg,
                ctx.deepMode() ? ChatMessage.MODE_DEEP : ChatMessage.MODE_QUICK,
                null, null, null
        );
        ctx.logBuilder().assistantMessageId(saved.id())
                        .status(ChatActivityLog.STATUS_MOCK)
                        .totalDurationMs(System.currentTimeMillis());
        activityLogService.saveLog(ctx.logBuilder().build());
        sendEvent(ctx.emitter(), "done", Map.of(
                "assistantMessageId", saved.id(),
                "sessionId", sessionId,
                "clarification", true
        ));
        ctx.emitter().complete();
        throw new StreamAbortException();
    }

    private void callLlmStream(
            StreamContext ctx,
            List<Map<String, String>> llmMessages,
            boolean useMock,
            String userContent,
            String modelCode
    ) throws Exception {
        ChatEndpoint endpoint = resolveChatEndpoint(ctx.scenario(), modelCode);
        ctx.logBuilder().llmEndpoint(endpoint.baseUrl()).llmModel(endpoint.model());
        ThinkBlockStreamParser thinkParser = ctx.deepMode() ? new ThinkBlockStreamParser() : null;

        long llmStart = System.currentTimeMillis();
        if (useMock) {
            ctx.logBuilder().status(ChatActivityLog.STATUS_MOCK);
            ctx.logBuilder().llmRequestJson(buildMockRequestNote(userContent));
            openAiClient.streamMockReply(userContent, token ->
                handleToken(ctx.emitter(), token, ctx.deepMode(), thinkParser, ctx.responseText, ctx.thinkText)
            );
        } else {
            String requestJson = buildLlmRequestJson(endpoint.model(), llmMessages);
            ctx.logBuilder().llmRequestJson(requestJson);
            OpenAiCompatibleStreamClient.StreamCompletionMeta streamMeta = openAiClient.streamChatCompletions(
                    endpoint.baseUrl(), endpoint.apiKey(), endpoint.model(), llmMessages,
                    token -> handleToken(ctx.emitter(), token, ctx.deepMode(), thinkParser, ctx.responseText, ctx.thinkText),
                    toolCall -> sendToolCallEvent(ctx.emitter(), toolCall)
            );
            ctx.logBuilder().llmDurationMs(System.currentTimeMillis() - llmStart);
            if (!streamMeta.sawDone() || "length".equals(streamMeta.finishReason())) {
                String msg = "length".equals(streamMeta.finishReason())
                        ? "回答可能因输出长度上限被截断，可调大环境变量 QIANXUN_LLM_MAX_TOKENS 或 qianxun.llm.max-tokens。"
                        : "上游流式连接在未正常结束时断开，若正文不完整请重试。";
                sendEvent(ctx.emitter(), "stream_warning", Map.of(
                        "finishReason", streamMeta.finishReason() == null ? "" : streamMeta.finishReason(),
                        "sawDone", streamMeta.sawDone(),
                        "message", msg
                ));
            }
        }
        if (thinkParser != null) {
            for (ThinkBlockStreamParser.Chunk chunk : thinkParser.flush()) {
                routeChunk(ctx.emitter(), chunk, ctx.responseText, ctx.thinkText);
            }
        }
        ctx.logBuilder().llmResponseText(ctx.responseText.toString());
        ctx.logBuilder().thinkContent(ctx.thinkText.isEmpty() ? null : ctx.thinkText.toString());
        if (ctx.deepMode()) {
            sendEvent(ctx.emitter(), "think_end", Map.of("thinkContent", ctx.thinkText.isEmpty() ? "" : ctx.thinkText));
        }
    }

    private void parseAndSendEntities(StreamContext ctx) {
        EntityBlockParser.Result entityParse = EntityBlockParser.parse(ctx.responseText.toString(), objectMapper);
        String answerClean = entityParse.cleanContent();
        ctx.logBuilder().llmResponseText(answerClean);

        JsonNode entityArr = entityParse.entitiesArray();
        String entityCardsJson = null;
        try {
            if (entityArr != null && entityArr.isArray() && !entityArr.isEmpty()) {
                entityCardsJson = objectMapper.writeValueAsString(entityArr);
            }
        } catch (Exception ex) {
            log.debug("实体 JSON 序列化失败（忽略）: {}", ex.toString());
        }
        try {
            String itemsJson = entityArr == null || entityArr.isNull() || !entityArr.isArray()
                    ? "[]" : objectMapper.writeValueAsString(entityArr);
            sendEvent(ctx.emitter(), "entities", Map.of("itemsJson", itemsJson));
        } catch (Exception ex) {
            log.debug("entities SSE 序列化失败（忽略）: {}", ex.toString());
        }
        ctx.logBuilder().nluRawResponse(entityCardsJson);
    }

    private void saveAndComplete(StreamContext ctx) {
        String sessionId = ctx.logBuilder().build().sessionId();
        String answer    = ctx.logBuilder().build().llmResponseText();
        String think     = ctx.logBuilder().build().thinkContent();
        String entities  = ctx.logBuilder().build().nluRawResponse();
        var saved = sessionService.appendAssistantMessage(
                sessionId, answer,
                ctx.deepMode() ? ChatMessage.MODE_DEEP : ChatMessage.MODE_QUICK,
                think, entities, ctx.intentAnalysisJson()
        );
        ctx.logBuilder().assistantMessageId(saved.id())
                        .totalDurationMs(System.currentTimeMillis());
        activityLogService.saveLog(ctx.logBuilder().build());
        sendEvent(ctx.emitter(), "done", Map.of(
                "assistantMessageId", saved.id(),
                "sessionId", sessionId
        ));
        ctx.emitter().complete();
    }

    private void handleStreamError(StreamContext ctx, long totalStart, Throwable ex) {
        if (ex instanceof StreamAbortException) { return; }
        log.warn("流式问答失败: {}", ex.toString());
        activityLogService.saveLog(
                ctx.logBuilder().status(ChatActivityLog.STATUS_ERROR)
                                  .errorMessage(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                                  .totalDurationMs(System.currentTimeMillis() - totalStart)
                                  .build()
        );
        try {
            sendEvent(ctx.emitter(), "error", Map.of("message", ex.getMessage() == null ? "unknown" : ex.getMessage()));
        } catch (Exception ignored) {}
        if (ex instanceof Exception e) {
            ctx.emitter().completeWithError(e);
        } else {
            ctx.emitter().completeWithError(new UncheckedIOException(new IOException(ex)));
        }
    }

    private static class StreamAbortException extends RuntimeException {
        StreamAbortException() { super(null, null, false, false); }
    }
}
