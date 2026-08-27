package com.qianxun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.ChatActivityLog;
import com.qianxun.domain.ChatMessage;
import com.qianxun.domain.ChatSession;
import com.qianxun.domain.DataFile;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.llm.ClaudeCodeChatClient;
import com.qianxun.llm.ClaudeCodePaths;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.llm.OpenAiCompatibleStreamClient;
import com.qianxun.llm.TokenUsageMerge;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.DataFileRepository;
import com.qianxun.repo.ModelRegistryRepository;
import com.qianxun.storage.FilePublicLinks;
import com.qianxun.storage.HermesGeneratedDocuments;
import com.qianxun.storage.UserDocumentStore;
import com.qianxun.service.stream.ActiveRunRegistry;
import com.qianxun.service.stream.ChatRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

@Service
public class QianXunServiceChatStream {

    private static final Logger log = LoggerFactory.getLogger(QianXunServiceChatStream.class);

    private final QianXunServiceChatSession sessionService;
    private final OpenAiCompatibleStreamClient openAiClient;
    private final QianXunServiceActivityLog activityLogService;
    private final ModelRegistryRepository modelRegistryRepository;
    private final AgentRegistryRepository agentRegistryRepository;
    private final ObjectMapper objectMapper;
    private final QianxunProperties properties;
    private final HermesAgentClient hermesAgentClient;
    private final HermesToolsetService hermesToolsetService;
    private final ToolDisplayNames toolDisplayNames;
    private final DataFileRepository dataFileRepository;
    private final UserDocumentStore userDocumentStore;
    private final ContextWindowResolver contextWindowResolver;
    private final HermesSkillService hermesSkillService;
    private final ClaudeCodeChatClient claudeCodeChatClient;
    private final ActiveRunRegistry activeRunRegistry;
    private final HermesLiveTranscriptService hermesLiveTranscriptService;
    private final SystemSettingsService systemSettingsService;

    public QianXunServiceChatStream(
            QianXunServiceChatSession sessionService,
            OpenAiCompatibleStreamClient openAiClient,
            QianXunServiceActivityLog activityLogService,
            ModelRegistryRepository modelRegistryRepository,
            AgentRegistryRepository agentRegistryRepository,
            ObjectMapper objectMapper,
            QianxunProperties properties,
            HermesAgentClient hermesAgentClient,
            HermesToolsetService hermesToolsetService,
            ToolDisplayNames toolDisplayNames,
            DataFileRepository dataFileRepository,
            UserDocumentStore userDocumentStore,
            ContextWindowResolver contextWindowResolver,
            HermesSkillService hermesSkillService,
            ClaudeCodeChatClient claudeCodeChatClient,
            ActiveRunRegistry activeRunRegistry,
            HermesLiveTranscriptService hermesLiveTranscriptService,
            SystemSettingsService systemSettingsService
    ) {
        this.sessionService = sessionService;
        this.openAiClient = openAiClient;
        this.activityLogService = activityLogService;
        this.modelRegistryRepository = modelRegistryRepository;
        this.agentRegistryRepository = agentRegistryRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.hermesAgentClient = hermesAgentClient;
        this.hermesToolsetService = hermesToolsetService;
        this.toolDisplayNames = toolDisplayNames;
        this.dataFileRepository = dataFileRepository;
        this.userDocumentStore = userDocumentStore;
        this.contextWindowResolver = contextWindowResolver;
        this.hermesSkillService = hermesSkillService;
        this.claudeCodeChatClient = claudeCodeChatClient;
        this.activeRunRegistry = activeRunRegistry;
        this.hermesLiveTranscriptService = hermesLiveTranscriptService;
        this.systemSettingsService = systemSettingsService;
    }

    public void streamAnswer(
            String userId, String sessionId,
            String userContent,
            String modelCode,
            String agentCode,
            String hermesProfile,
            List<String> fileIds,
            String skillName,
            com.qianxun.web.dto.SessionGoalRequest goal,
            Boolean clearGoal,
            Boolean agentsStatus,
            String slashCommand,
            ChatRun run
    ) {
        long totalStart = System.currentTimeMillis();
        String logId = UUID.randomUUID().toString().replace("-", "");
        ChatActivityLog.Builder logBuilder = ChatActivityLog.builder()
                .id(logId)
                .userId(userId)
                .sessionId(sessionId)
                .userContent(userContent)
                .createdAt(Instant.now());

        StreamContext ctx = new StreamContext(run, logBuilder, userId);
        String effectiveAgentCode = agentCode;
        String effectiveHermesProfile = hermesProfile;
        ChatSession bound = sessionService.findOwnedOrNull(sessionId, userId);
        if (bound != null) {
            if (isBlank(effectiveAgentCode) && !isBlank(bound.agentCode())) {
                effectiveAgentCode = bound.agentCode();
            }
            if (isBlank(effectiveHermesProfile) && !isBlank(bound.hermesProfile())) {
                effectiveHermesProfile = bound.hermesProfile();
            }
        }
        Optional<AgentRegistryItem> activeAgent = resolveActiveAgent(effectiveAgentCode);
        ctx.setActiveAgent(activeAgent);
        ctx.setHermesProfile(resolveHermesProfile(effectiveHermesProfile, activeAgent));
        run.setHermesProfile(ctx.hermesProfile());
        run.setAgentCode(effectiveAgentCode == null ? "" : effectiveAgentCode);
        run.setModelCode(modelCode == null ? "" : modelCode);
        if (!ctx.hermesProfile().isBlank()) {
            hermesAgentClient.ensureProfileApiKey(ctx.hermesProfile());
            hermesAgentClient.createProfile(userId, ctx.hermesProfile(),
                    activeAgent.map(AgentRegistryItem::description).orElse(""));
        }
        String agentDisplay = activeAgent.map(AgentRegistryItem::name).orElse("");
        sessionService.bindAgentIfEmpty(sessionId, userId, effectiveAgentCode, ctx.hermesProfile(), agentDisplay);

        try {
            if (properties.isAgentRunnerEnabled()) {
                hermesToolsetService.ensureChatGatewaySynced(ctx.hermesProfile(), false);
            }

            ForcedSkill forced = resolveForcedSkill(ctx, skillName);

            boolean clearGoalFlag = Boolean.TRUE.equals(clearGoal);
            boolean agentsStatusFlag = Boolean.TRUE.equals(agentsStatus);
            ChatGoalInvocation.Goal incomingGoal = ChatGoalInvocation.fromRequest(goal);
            boolean kickoffGoal = false;
            ChatGoalInvocation.Goal activeGoal;
            if (clearGoalFlag) {
                sessionService.clearGoal(sessionId, userId);
                activeGoal = ChatGoalInvocation.fromRequest(null);
            } else if (!incomingGoal.isBlank()) {
                if (incomingGoal.title().isBlank()) {
                    throw new IllegalArgumentException("请填写目标标题");
                }
                activeGoal = sessionService.persistGoal(sessionId, userId, incomingGoal);
                kickoffGoal = true;
            } else {
                activeGoal = sessionService.loadGoal(sessionId);
            }
            boolean planRelated = forced != null && ChatPlanInvocation.isPlanRelated(forced.name());
            if (properties.isAgentRunnerEnabled() && (!activeGoal.isBlank() || planRelated)) {
                hermesToolsetService.ensureChatGatewaySynced(ctx.hermesProfile(), true);
            }

            String agentsStatusQuery = agentsStatusFlag ? ChatAgentsInvocation.extractStatusQuery(userContent) : "";
            String storedUserContent = userContent;
            if (agentsStatusFlag && (storedUserContent == null || storedUserContent.isBlank()
                    || ChatAgentsInvocation.looksLikeCommand(storedUserContent))) {
                storedUserContent = ChatAgentsInvocation.displayContent(agentsStatusQuery);
            }
            String planUpstreamTask = null;
            if (forced != null && planRelated) {
                planUpstreamTask = ChatPlanInvocation.defaultTaskForSkill(forced.name(), storedUserContent);
                if (ChatPlanInvocation.isExecuteSkill(forced.name())
                        && (storedUserContent == null || storedUserContent.isBlank()
                        || storedUserContent.startsWith("【执行计划】"))) {
                    storedUserContent = ChatPlanInvocation.LOCAL_EXECUTE_DISPLAY;
                } else if (ChatPlanInvocation.isPlanSkill(forced.name())
                        && (storedUserContent == null || storedUserContent.isBlank())) {
                    storedUserContent = ChatPlanInvocation.LOCAL_CREATE_PREFIX + "按当前对话生成";
                }
            }
            ChatMessage userMsg = sessionService.appendUserMessage(sessionId, storedUserContent);
            logBuilder.userMessageId(userMsg.id());

            // 草稿时间必须严格晚于用户消息，避免 DATETIME 秒级截断 + UUID 排序把答排到问前面
            Instant draftAt = Instant.now();
            Instant userAt = userMsg.createdAt() == null ? draftAt : userMsg.createdAt();
            if (!draftAt.isAfter(userAt)) {
                draftAt = userAt.plusMillis(1);
            }
            ChatMessage draft = sessionService.appendAssistantMessage(
                    sessionId, "", null, null, null,
                    ChatMessage.STATUS_STREAMING, run.runId(), draftAt
            );
            run.setAssistantMessageId(draft.id());
            ctx.assistantMessageId = draft.id();

            // 草稿就绪后再发 started，便于订阅端拿到 assistantMessageId
            publish(ctx, "started", Map.of("sessionId", sessionId));
            publish(ctx, "session_goal", Map.of(
                    "cleared", clearGoalFlag || activeGoal.isBlank(),
                    "goal", activeGoal.isBlank() ? Map.of() : Map.of(
                            "title", activeGoal.title(),
                            "description", activeGoal.description(),
                            "steps", activeGoal.steps(),
                            "constraints", activeGoal.constraints()
                    )
            ));

            boolean useMock = validateConfig();
            boolean useDashboard = !useMock && properties.isAgentRunnerEnabled();

            if (agentsStatusFlag && !useDashboard) {
                handleToken(ctx, ChatAgentsInvocation.unavailableMessage());
                saveAndComplete(ctx);
                return;
            }

            List<ChatMessage> history = sessionService.loadHistoryForLlm(sessionId);
            List<Map<String, String>> storedMessages = toOpenAiMessages(history);
            List<Map<String, String>> llmMessages = withAttachedFileContext(userId, fileIds, storedMessages);
            if (planUpstreamTask != null && !planUpstreamTask.isBlank()) {
                llmMessages = replaceLastUserContent(llmMessages, planUpstreamTask);
            }
            // Dashboard：技能由原生 /{slug} slash 展开，禁止再双重注入 SKILL.md
            if (forced != null && !useDashboard) {
                llmMessages = ChatSkillInvocation.apply(llmMessages, forced.name(), forced.md());
            }
            String dashboardSkill = (forced != null && useDashboard) ? forced.name() : null;
            ChatDashboardTurn.Plan dashboardPlan = ChatDashboardTurn.plan(
                    clearGoalFlag, kickoffGoal, activeGoal, dashboardSkill, agentsStatusFlag, slashCommand,
                    storedMessages, llmMessages);
            if (planRelated && useDashboard) {
                log.info("Dashboard /plan session={} skill={}", sessionId, dashboardSkill);
            }
            if (!useDashboard) {
                llmMessages = withGeneratedDocHint(llmMessages);
                if (clearGoalFlag) {
                    llmMessages = ChatGoalInvocation.applyClear(llmMessages);
                    log.info("goal clear session={} (completions rewrite)", sessionId);
                } else if (!activeGoal.isBlank()) {
                    llmMessages = ChatGoalInvocation.apply(llmMessages, activeGoal, kickoffGoal);
                    if (kickoffGoal) {
                        log.info("goal kickoff session={} title={} (completions rewrite)", sessionId, activeGoal.title());
                    }
                }
            } else if (clearGoalFlag) {
                log.info("Dashboard /goal clear session={}", sessionId);
            } else if (agentsStatusFlag) {
                log.info("Dashboard /agents session={}", sessionId);
            } else if (kickoffGoal) {
                log.info("Dashboard /goal kickoff session={} title={}", sessionId, activeGoal.title());
            } else if (dashboardSkill != null) {
                log.info("Dashboard /skill session={} skill={}", sessionId, dashboardSkill);
            }

            ctx.awaitGoalContinuations = useDashboard && !clearGoalFlag && !agentsStatusFlag && !activeGoal.isBlank();
            callLlmStream(ctx, llmMessages, useMock, useDashboard, dashboardPlan, storedUserContent, modelCode, ctx.hermesProfile(), sessionId);
            if (run.isCancelRequested()) {
                throw new CancellationException("run cancelled");
            }
            if (agentsStatusFlag && useDashboard) {
                try {
                    String appendix = hermesLiveTranscriptService.formatChatAppendix(
                            ctx.hermesProfile(), agentsStatusQuery);
                    if (appendix != null && !appendix.isBlank()) {
                        handleToken(ctx, appendix);
                    }
                } catch (Exception ex) {
                    log.debug("附加 live transcript 失败 session={}: {}", sessionId, ex.toString());
                    handleToken(ctx, "\n\n（live transcript 读取失败：" + ex.getMessage() + "）\n");
                }
            }
            if (!agentsStatusFlag) {
                generateAndSendSuggestions(ctx, history, storedUserContent, modelCode, useMock);
            }
            saveAndComplete(ctx);

        } catch (CancellationException ex) {
            handleStreamCancelled(ctx, totalStart);
        } catch (Exception ex) {
            if (run.isCancelRequested()) {
                handleStreamCancelled(ctx, totalStart);
            } else {
                handleStreamError(ctx, totalStart, ex);
            }
        } finally {
            run.clearInterruptHook();
        }
    }

    /** 再附着进行中（或宽限期内）的 Run。 */
    public SseEmitter subscribe(String userId, String sessionId, long afterSeq) {
        sessionService.ensureSessionOwnership(sessionId, userId);
        ChatRun run = activeRunRegistry.findBySession(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前会话没有可附着的输出"));
        if (!userId.equals(run.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权附着该会话输出");
        }
        SseEmitter emitter = new SseEmitter(0L);
        run.addSubscriber(emitter, Math.max(0, afterSeq));
        return emitter;
    }

    public Optional<com.qianxun.web.dto.ActiveRunResponse> activeRun(String userId, String sessionId) {
        sessionService.ensureSessionOwnership(sessionId, userId);
        return activeRunRegistry.findBySession(sessionId)
                .filter(r -> userId.equals(r.userId()))
                .map(com.qianxun.web.dto.ActiveRunResponse::from);
    }

    public void stopRun(String userId, String sessionId) {
        sessionService.ensureSessionOwnership(sessionId, userId);
        ChatRun run = activeRunRegistry.findRunning(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前会话没有进行中的输出"));
        if (!userId.equals(run.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权停止该会话输出");
        }
        run.requestCancel();
    }

    // ── Token 路由 ─────────────────────────────────────────────────────────────

    private void handleToken(StreamContext ctx, String token) {
        if (ctx.run().isCancelRequested()) {
            throw new CancellationException("run cancelled");
        }
        ctx.responseText.append(token);
        ctx.run().setContentSnapshot(ctx.responseText.toString());
        publish(ctx, "token", Map.of("text", token));
        maybeFlushDraft(ctx);
    }

    private void publish(StreamContext ctx, String name, Map<String, Object> data) {
        ctx.run().publish(name, data);
    }

    private void publishTerminal(StreamContext ctx, String name, Map<String, Object> data) {
        ctx.run().publishTerminal(name, data);
    }

    private void sendToolCallEvent(StreamContext ctx, OpenAiCompatibleStreamClient.ToolCallEvent tc) {
        if (ctx.run().isCancelRequested()) {
            throw new CancellationException("run cancelled");
        }
        boolean[] created = { false };
        Map<String, Object> data = upsertToolCall(ctx, tc, created);
        if (data == null || data.isEmpty()) {
            return;
        }
        if (created[0]) {
            ctx.run().incrementToolCalls(1);
            String toolName = String.valueOf(data.getOrDefault("toolName", ""));
            if (isDelegationTool(toolName)) {
                ctx.run().incrementDelegations(1);
            }
        }
        String toolName = String.valueOf(data.getOrDefault("toolName", ""));
        if (isDelegationTool(toolName)) {
            publish(ctx, "delegation_update", delegationUpdatePayload(data, toolName));
        }
        publish(ctx, "tool_call", data);
        maybeFlushDraft(ctx);
    }

    private void maybeFlushDraft(StreamContext ctx) {
        if (ctx.assistantMessageId == null || ctx.assistantMessageId.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - ctx.lastDraftFlushAt < 500) {
            return;
        }
        ctx.lastDraftFlushAt = now;
        flushDraft(ctx, ChatMessage.STATUS_STREAMING);
    }

    private void flushDraft(StreamContext ctx, String status) {
        if (ctx.assistantMessageId == null || ctx.assistantMessageId.isBlank()) {
            return;
        }
        String toolCallsJson = null;
        String usageJson = null;
        String suggestionsJson = null;
        try {
            if (!ctx.toolCalls.isEmpty()) {
                toolCallsJson = objectMapper.writeValueAsString(ctx.toolCalls);
            }
            if (ctx.usage != null && !ctx.usage.isEmpty()) {
                usageJson = objectMapper.writeValueAsString(ctx.usage);
            }
            if (ctx.suggestions != null && !ctx.suggestions.isEmpty()) {
                suggestionsJson = objectMapper.writeValueAsString(ctx.suggestions);
            }
        } catch (Exception ex) {
            log.debug("draft JSON 序列化失败（忽略）: {}", ex.toString());
        }
        try {
            sessionService.updateAssistantMessage(
                    ctx.assistantMessageId,
                    ctx.responseText.toString(),
                    toolCallsJson,
                    usageJson,
                    suggestionsJson,
                    status
            );
        } catch (Exception ex) {
            log.warn("助手草稿落库失败: {}", ex.toString());
        }
    }

    private Map<String, Object> upsertToolCall(StreamContext ctx, OpenAiCompatibleStreamClient.ToolCallEvent tc, boolean[] createdOut) {
        boolean idFromUpstream = tc.toolCallId() != null && !tc.toolCallId().isBlank();
        String id = idFromUpstream
                ? tc.toolCallId().trim()
                : "call_" + (ctx.toolCalls.size() + 1);
        String incomingName = tc.functionName() == null ? "" : tc.functionName().trim();
        Map<String, Object> row = null;
        for (Map<String, Object> existing : ctx.toolCalls) {
            if (id.equals(String.valueOf(existing.get("toolCallId")))) {
                row = existing;
                break;
            }
        }
        // 仅无上游 id 的增量（参数分片）才按同名未完成项合并；并行子智能体各有 id，必须分列
        if (row == null && !idFromUpstream && !incomingName.isBlank()) {
            String incomingParent = parentIdOf(tc);
            for (int i = ctx.toolCalls.size() - 1; i >= 0; i--) {
                Map<String, Object> existing = ctx.toolCalls.get(i);
                String status = String.valueOf(existing.getOrDefault("status", ""));
                String name = String.valueOf(existing.getOrDefault("toolName", ""));
                if (incomingName.equals(name) && !"completed".equals(status) && !"error".equals(status)
                        && incomingParent.equals(String.valueOf(existing.getOrDefault("parentId", "")))) {
                    row = existing;
                    row.put("toolCallId", id);
                    break;
                }
            }
        }
        // 仅有 tool_result、无法关联到已有 tool_use 时，不生成空名行（避免前端 unknown_tool）
        if (row == null && incomingName.isBlank()) {
            return Map.of();
        }
        if (row == null) {
            row = new LinkedHashMap<>();
            row.put("toolCallId", id);
            row.put("startedAt", tc.startedAt() != null ? tc.startedAt() : System.currentTimeMillis());
            row.put("contentOffset", ctx.responseText.length());
            ctx.toolCalls.add(row);
            if (createdOut != null && createdOut.length > 0) {
                createdOut[0] = true;
            }
        }
        if (tc.functionName() != null && !tc.functionName().isBlank()) {
            row.put("toolName", tc.functionName());
        }
        Object nameObj = row.get("toolName");
        if (nameObj != null && !String.valueOf(nameObj).isBlank()) {
            String code = String.valueOf(nameObj).trim();
            row.put("displayName", toolDisplayNames.displayName(code));
            row.put("iconKind", toolDisplayNames.iconKind(code));
        } else {
            row.putIfAbsent("displayName", "工具");
            row.putIfAbsent("iconKind", "gear");
        }
        if (tc.argsChunk() != null && !tc.argsChunk().isBlank()) {
            String incoming = tc.argsChunk();
            Object prev = row.get("args");
            if (prev == null || incoming.trim().startsWith("{") || incoming.trim().startsWith("[")) {
                row.put("args", incoming);
            } else {
                row.put("args", String.valueOf(prev) + incoming);
            }
        }
        if (tc.result() != null && !tc.result().isBlank()) {
            row.put("result", tc.result());
        }
        if (tc.status() != null && !tc.status().isBlank()) {
            applyIncomingStatus(row, tc.status());
        }
        // 后台等待中：不要保留派工瞬间的 endedAt，否则前端会显示已结束
        if ("awaiting".equals(String.valueOf(row.get("status")))) {
            row.remove("endedAt");
        }
        if (tc.startedAt() != null && row.get("startedAt") == null) {
            row.put("startedAt", tc.startedAt());
        }
        if (tc.endedAt() != null && !"awaiting".equals(String.valueOf(row.get("status")))) {
            row.put("endedAt", tc.endedAt());
        }
        mergeToolDetails(row, tc.details());
        attachSubagentToOpenDelegation(ctx, row, incomingName);
        String stAfter = String.valueOf(row.getOrDefault("status", "")).toLowerCase();
        Object eventType = row.get("eventType");
        if (("completed".equals(stAfter) || "error".equals(stAfter))
                && eventType != null
                && String.valueOf(eventType).toLowerCase().contains("subagent.complete")) {
            closeDescendantToolCalls(ctx, String.valueOf(row.getOrDefault("toolCallId", "")), stAfter);
        }
        Object started = row.get("startedAt");
        Object ended = row.get("endedAt");
        if (started instanceof Number s && ended instanceof Number e) {
            row.put("durationMs", Math.max(0, e.longValue() - s.longValue()));
        } else if (row.get("durationMs") == null && row.get("durationSeconds") instanceof Number ds) {
            row.put("durationMs", Math.max(0, Math.round(ds.doubleValue() * 1000.0)));
            if (started instanceof Number s0 && row.get("endedAt") == null
                    && ("completed".equals(row.get("status")) || "error".equals(row.get("status")))) {
                row.put("endedAt", s0.longValue() + ((Number) row.get("durationMs")).longValue());
            }
        }
        ingestGeneratedDocuments(ctx, row);
        return row;
    }

    /** Dashboard 子智能体事件若未带 parent_id，挂到当前未结束的委派工具下，便于前端并排展示。 */
    private static void attachSubagentToOpenDelegation(
            StreamContext ctx, Map<String, Object> row, String incomingName) {
        if (row == null || incomingName == null) {
            return;
        }
        if (!"subagent".equalsIgnoreCase(incomingName.trim())) {
            return;
        }
        Object existingParent = row.get("parentId");
        if (existingParent != null && !String.valueOf(existingParent).isBlank()) {
            return;
        }
        for (int i = ctx.toolCalls.size() - 1; i >= 0; i--) {
            Map<String, Object> existing = ctx.toolCalls.get(i);
            if (existing == row) {
                continue;
            }
            String name = String.valueOf(existing.getOrDefault("toolName", ""));
            if (!isDelegationTool(name) || "subagent".equalsIgnoreCase(name)) {
                continue;
            }
            String status = String.valueOf(existing.getOrDefault("status", "")).toLowerCase();
            if ("completed".equals(status) || "error".equals(status)) {
                continue;
            }
            Object pid = existing.get("toolCallId");
            if (pid != null && !String.valueOf(pid).isBlank()) {
                row.put("parentId", String.valueOf(pid));
            }
            return;
        }
    }

    private static String parentIdOf(OpenAiCompatibleStreamClient.ToolCallEvent tc) {
        if (tc == null || tc.details() == null) {
            return "";
        }
        Object v = tc.details().get("parentId");
        return v == null ? "" : String.valueOf(v).trim();
    }

    /**
     * {@link Map#of} 不允许 null；并行派工时尚未有 taskIndex，会在开场白之后把整轮 SSE 打挂。
     */
    static Map<String, Object> delegationUpdatePayload(Map<String, Object> data, String toolName) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", data.getOrDefault("toolCallId", ""));
        payload.put("toolName", toolName == null ? "" : toolName);
        payload.put("status", data.getOrDefault("status", ""));
        Object delegationId = data.get("delegationId");
        if (delegationId == null) {
            delegationId = data.get("childSessionId");
        }
        payload.put("delegationId", delegationId == null ? "" : delegationId);
        Object taskIndex = data.get("taskIndex");
        if (taskIndex != null) {
            payload.put("taskIndex", taskIndex);
        }
        Object summary = data.get("summary");
        if (summary == null || String.valueOf(summary).isBlank()) {
            summary = data.get("result");
        }
        payload.put("summary", summary == null ? "" : summary);
        return payload;
    }

    private static boolean isDelegationTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String n = toolName.trim().toLowerCase();
        return n.equals("agent") || n.equals("task") || n.equals("sendmessage")
                || n.contains("delegate") || n.contains("subagent");
    }

    /** 把 Dashboard 工具附加字段并入 SSE payload（不覆盖已有非空核心字段）。 */
    private static void mergeToolDetails(Map<String, Object> row, Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : details.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (key == null || key.isBlank() || val == null) {
                continue;
            }
            if ("eventType".equals(key)) {
                row.put("eventType", val);
                continue;
            }
            if ("progress".equals(key) || "summary".equals(key)) {
                // 进度/摘要允许覆盖，便于子智能体心跳刷新
                row.put(key, val);
                if ("progress".equals(key)) {
                    Object prev = row.get("result");
                    String chunk = String.valueOf(val);
                    if (prev == null || String.valueOf(prev).isBlank()) {
                        row.put("result", chunk);
                    } else if (!String.valueOf(prev).contains(chunk)) {
                        // awaiting 时用最新进度替换大段 result 噪声
                        if ("awaiting".equals(String.valueOf(row.get("status")))) {
                            row.put("result", chunk);
                        } else {
                            row.put("result", String.valueOf(prev) + chunk);
                        }
                    }
                }
                continue;
            }
            Object existing = row.get(key);
            if (existing == null || String.valueOf(existing).isBlank()) {
                row.put(key, val);
            }
        }
        Object err = details.get("error");
        if (err != null && !String.valueOf(err).isBlank()) {
            row.put("error", err);
            if (!"completed".equals(row.get("status"))) {
                row.putIfAbsent("status", "error");
            }
        }
    }

    private void ingestGeneratedDocuments(StreamContext ctx, Map<String, Object> row) {
        if (!properties.isAgentRunnerEnabled()) {
            return;
        }
        String status = String.valueOf(row.getOrDefault("status", ""));
        if (!"completed".equalsIgnoreCase(status)) {
            return;
        }
        String toolName = String.valueOf(row.getOrDefault("toolName", ""));
        String args = row.get("args") == null ? "" : String.valueOf(row.get("args"));
        String result = row.get("result") == null ? "" : String.valueOf(row.get("result"));
        List<String> paths = HermesGeneratedDocuments.pathsFromTool(objectMapper, toolName, args, result);
        if (paths.isEmpty()) {
            return;
        }
        int maxMb = properties.getMinio() == null ? 50 : Math.max(1, properties.getMinio().getMaxFileSizeMb());
        long maxBytes = maxMb * 1024L * 1024L;
        for (String path : paths) {
            ingestOneGeneratedDocument(ctx, path, maxBytes);
        }
    }

    private void ingestGeneratedDocumentsFromReply(StreamContext ctx) {
        if (!properties.isAgentRunnerEnabled()) {
            return;
        }
        List<String> paths = HermesGeneratedDocuments.pathsFromAssistantText(ctx.responseText.toString());
        if (paths.isEmpty()) {
            return;
        }
        int maxMb = properties.getMinio() == null ? 50 : Math.max(1, properties.getMinio().getMaxFileSizeMb());
        long maxBytes = maxMb * 1024L * 1024L;
        for (String path : paths) {
            ingestOneGeneratedDocument(ctx, path, maxBytes);
        }
    }

    /** Claude Code 常用 Bash/python 写 xlsx，工具名不是 Write，按本轮工作区新文件补入库。 */
    private void ingestGeneratedDocumentsFromWorkspace(StreamContext ctx) {
        if (!properties.isAgentRunnerEnabled()) {
            return;
        }
        String userId = ctx.userId();
        if (userId == null || userId.isBlank()) {
            return;
        }
        String ws = ClaudeCodePaths.workspace(properties, userId);
        HermesAgentClient.ManagedDirList listed = hermesAgentClient.listManagedDirectory(
                userId, ctx.hermesProfile(), ws, true);
        if (!listed.ok() || listed.entries() == null) {
            log.warn("扫描工作区入库失败 user={} ws={} msg={}", userId, ws, listed.message());
            return;
        }
        int maxMb = properties.getMinio() == null ? 50 : Math.max(1, properties.getMinio().getMaxFileSizeMb());
        long maxBytes = maxMb * 1024L * 1024L;
        long since = Math.max(0L, ctx.startedAt() - 120_000L);
        int considered = 0;
        for (HermesAgentClient.ManagedDirEntry e : listed.entries()) {
            if (e == null || e.directory() || !HermesGeneratedDocuments.isDocumentFilename(e.name())) {
                continue;
            }
            considered++;
            if (e.mtimeMs() != null && e.mtimeMs() < since) {
                continue;
            }
            String path = e.path() == null || e.path().isBlank() ? ws + "/" + e.name() : e.path();
            ingestOneGeneratedDocument(ctx, path, maxBytes);
        }
        if (considered > 0) {
            log.info("工作区扫描到 {} 个候选文档 user={} ws={}", considered, userId, ws);
        }
    }

    private void ingestOneGeneratedDocument(StreamContext ctx, String path, long maxBytes) {
        if (path == null || path.isBlank()) {
            return;
        }
        String key = HermesGeneratedDocuments.filenameOf(path).toLowerCase(java.util.Locale.ROOT);
        if (key.isBlank()) {
            key = path;
        }
        if (ctx.ingestedPaths.contains(path) || ctx.ingestedPaths.contains(key)) {
            ctx.ingestedPaths.add(path);
            ctx.ingestedPaths.add(key);
            return;
        }
        ctx.ingestedPaths.add(path);
        ctx.ingestedPaths.add(key);
        try {
            HermesAgentClient.DownloadedFile file = hermesAgentClient.downloadGeneratedDocument(
                    ctx.userId(), ctx.hermesProfile(), path);
            if (!file.ok() || file.bytes() == null || file.bytes().length == 0) {
                log.warn("跳过入库 Hermes 文件 {}：{}", path, file.message());
                ctx.ingestedPaths.remove(path);
                ctx.ingestedPaths.remove(key);
                return;
            }
            if (file.bytes().length > maxBytes) {
                log.warn("Hermes 生成文件过大，跳过入库 {} ({} bytes)", path, file.bytes().length);
                return;
            }
            String filename = HermesGeneratedDocuments.filenameOf(
                    file.filename() == null || file.filename().isBlank() ? path : file.filename());
            if (!HermesGeneratedDocuments.isDocumentFilename(filename)) {
                return;
            }
            var saved = userDocumentStore.persistBytes(
                    ctx.userId(),
                    filename,
                    file.bytes(),
                    UserDocumentStore.guessContentType(filename),
                    HermesGeneratedDocuments.FOLDER
            );
            String href = FilePublicLinks.relativePath(saved.publicToken());
            if (href.isBlank() || ctx.responseText.indexOf(href) >= 0) {
                return;
            }
            String md = HermesGeneratedDocuments.chatMarkdown(saved.name(), href);
            handleToken(ctx, md);
            publish(ctx, "generated_file", Map.of(
                    "id", saved.id(),
                    "name", saved.name(),
                    "kind", saved.kind(),
                    "publicToken", saved.publicToken() == null ? "" : saved.publicToken(),
                    "href", href
            ));
        } catch (Exception ex) {
            ctx.ingestedPaths.remove(path);
            ctx.ingestedPaths.remove(key);
            log.warn("入库 Hermes 生成文档失败 {}: {}", path, ex.toString());
        }
    }

    private void sendUsageEvent(StreamContext ctx, OpenAiCompatibleStreamClient.TokenUsage usage, int contextWindow) {
        if (usage == null) {
            return;
        }
        // 透传上游 tokenizer；同轮多次 usage（工具循环 / goal 续轮）累加，不自行估算
        Map<String, Object> data = TokenUsageMerge.accumulate(ctx.usage, usage, contextWindow);
        if (data == null) {
            return;
        }
        ctx.usage = data;
        publish(ctx, "usage", data);
    }

    private void sendCompactEvent(StreamContext ctx, String phase, String trigger, Integer preTokens) {
        if (phase == null || phase.isBlank()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phase", phase);
        data.put("trigger", trigger == null ? "" : trigger);
        if (preTokens != null) {
            data.put("preTokens", preTokens);
        }
        publish(ctx, "compact", data);
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
        if (properties.isAgentRunnerEnabled()) {
            return false;
        }
        QianxunProperties.Llm llm = properties.getLlm();
        String key = llm.getApiKey() == null ? "" : llm.getApiKey().trim();
        if (!key.isEmpty()) {
            return false;
        }
        return llm.isMockEnabled();
    }

    private Optional<AgentRegistryItem> resolveActiveAgent(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return Optional.empty();
        }
        return agentRegistryRepository.findByCode(agentCode.trim()).filter(AgentRegistryItem::enabled);
    }

    private String resolveHermesProfile(String requested, Optional<AgentRegistryItem> activeAgent) {
        if (requested != null && !requested.isBlank()) {
            return hermesAgentClient.normalizeProfileName(requested);
        }
        if (activeAgent.isPresent()) {
            String bound = activeAgent.get().hermesProfile();
            if (bound != null && !bound.isBlank()) {
                return hermesAgentClient.normalizeProfileName(bound);
            }
        }
        if (properties.isAgentRunnerEnabled() && activeAgent.isEmpty()) {
            return "default";
        }
        return "";
    }

    private ChatEndpoint resolveChatEndpoint(
            String selectedModelCode,
            String hermesProfile
    ) {
        if (properties.isAgentRunnerEnabled()) {
            QianxunProperties.Claude claude = properties.getClaude();
            String model = blankOrDefault(systemSettingsService.resolvedClaudeChatModel(), "claude-sonnet-4-5");
            return new ChatEndpoint(
                    hermesAgentClient.chatBaseUrlForProfile(hermesProfile),
                    claude.getApiKey(),
                    model
            );
        }
        if (selectedModelCode != null && !selectedModelCode.isBlank()) {
            var selected = modelRegistryRepository.findByCode(selectedModelCode.trim());
            if (selected.isPresent() && selected.get().enabled()) {
                return endpointFromRegistryModel(selected.get());
            }
        }
        QianxunProperties.Llm llm = properties.getLlm();
        return new ChatEndpoint(trim(llm.getBaseUrl()), llm.getApiKey(), llm.getModel());
    }

    /**
     * 模型注册表项 → 实际调用的 OpenAI 兼容端点。
     * 若 base_url 与当前 Hermes 配置一致，必须使用 Hermes 的 API Key（与 OPENAI_API_KEY 解耦），否则会 401。
     */
    private ChatEndpoint endpointFromRegistryModel(ModelRegistryItem m) {
        String baseUrl = trim(m.baseUrl());
        String apiKey = resolveRegistryModelApiKey(m);
        String model = resolveRegistryUpstreamModel(m);
        return new ChatEndpoint(baseUrl, apiKey, model);
    }

    private boolean registryBaseMatchesHermes(String registryBaseUrl) {
        return false;
    }

    private static String normalizeOpenAiBase(String url) {
        if (url == null) {
            return "";
        }
        return url.trim().replaceAll("/+\\s*$", "").toLowerCase();
    }

    private String resolveRegistryModelApiKey(ModelRegistryItem m) {
        String baseUrl = m.baseUrl() == null ? "" : m.baseUrl().trim();
        if (registryBaseMatchesHermes(baseUrl)) {
            return coalesce(properties.getClaude().getApiKey(), properties.getLlm().getApiKey());
        }
        String p = m.provider() == null ? "" : m.provider().trim().toLowerCase();
        if ("hermes".equals(p) || "claude".equals(p) || "anthropic".equals(p)) {
            return coalesce(properties.getClaude().getApiKey(), properties.getLlm().getApiKey());
        }
        if ("kimi-coding".equals(p)) {
            return coalesce(System.getenv("KIMI_API_KEY"), properties.getLlm().getApiKey());
        }
        if (baseUrl.toLowerCase().contains("moonshot.cn")) {
            return coalesce(System.getenv("KIMI_API_KEY"), properties.getLlm().getApiKey());
        }
        return trim(properties.getLlm().getApiKey());
    }

    /**
     * registry.code 一般即上游 model；旧种子 qianxun-default 需映射为 {@link QianxunProperties.Hermes#getChatModel()}。
     */
    private String resolveRegistryUpstreamModel(ModelRegistryItem m) {
        String code = m.code() == null ? "" : m.code().trim();
        String baseUrl = m.baseUrl() == null ? "" : m.baseUrl().trim();
        if (!registryBaseMatchesHermes(baseUrl)) {
            return code;
        }
        String chat = trim(systemSettingsService.resolvedClaudeChatModel());
        if ("qianxun-default".equalsIgnoreCase(code) || "hermes-agent".equalsIgnoreCase(code)
                || "claude-code".equalsIgnoreCase(code)) {
            return blankOrDefault(chat, "claude-sonnet-4-5");
        }
        if (code.isEmpty()) {
            return blankOrDefault(chat, "claude-sonnet-4-5");
        }
        return blankOrDefault(code, chat);
    }

    private static List<Map<String, String>> toOpenAiMessages(List<ChatMessage> history) {
        List<Map<String, String>> out = new ArrayList<>();
        for (ChatMessage m : history) {
            if (!"user".equals(m.role()) && !"assistant".equals(m.role()) && !"system".equals(m.role())) { continue; }
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            row.put("role", m.role());
            row.put("content", m.content());
            out.add(row);
        }
        return out;
    }

    /** 把发给上游的本轮 user 任务改成完整指令（气泡仍可存短文案）。 */
    private static List<Map<String, String>> replaceLastUserContent(
            List<Map<String, String>> messages,
            String content
    ) {
        if (messages == null || messages.isEmpty() || content == null) {
            return messages;
        }
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> m = messages.get(i);
            if (m != null && "user".equals(m.get("role"))) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0) {
            return messages;
        }
        List<Map<String, String>> out = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> m = messages.get(i);
            if (i == lastUser) {
                out.add(Map.of("role", "user", "content", content));
            } else if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * 仅注入本轮聊天框上传的附件。fileIds 为空时不访问网盘、不抽取任何文件正文。
     */
    private List<Map<String, String>> withAttachedFileContext(
            String userId, List<String> fileIds, List<Map<String, String>> messages
    ) {
        List<String> attached = ChatFileContext.attachedIds(fileIds);
        if (attached.isEmpty()) {
            return messages;
        }
        List<DataFile> attachedFiles = dataFileRepository.findByIds(new ArrayList<>(attached)).stream()
                .filter(f -> userId.equals(f.userId()) && !f.isFolder())
                .toList();
        return ChatFileContext.apply(attached, attachedFiles, messages, properties);
    }

    private record ForcedSkill(String name, String md) {}

    private ForcedSkill resolveForcedSkill(StreamContext ctx, String skillName) {
        String want = trim(skillName);
        if (want.isEmpty()) {
            return null;
        }
        ctx.logBuilder().nluAgentSkill(want);
        if (!properties.isAgentRunnerEnabled()) {
            return new ForcedSkill(want, "");
        }
        List<HermesAgentClient.SkillInfo> skills;
        try {
            skills = hermesSkillService.list(ctx.hermesProfile());
        } catch (RuntimeException ex) {
            log.debug("列出技能失败，仍向模型注入技能指令: {}", ex.toString());
            return new ForcedSkill(want, "");
        }
        HermesAgentClient.SkillInfo hit = ChatSkillInvocation.findEnabled(skills, want);
        if (hit == null) {
            throw new IllegalArgumentException(
                    ChatSkillInvocation.refuseMessage(ChatSkillInvocation.exists(skills, want), want));
        }
        // Dashboard 原生 slash 会自行加载 SKILL.md；此处仅校验启用，避免双重注入时再拉全文
        boolean dashboard = !shouldUseMock() && properties.isAgentRunnerEnabled();
        if (dashboard) {
            return new ForcedSkill(hit.name(), "");
        }
        String md = "";
        try {
            HermesAgentClient.SkillContentResult body = hermesAgentClient.getSkillContent(
                    ctx.userId(), ctx.hermesProfile(), hit.name());
            if (body != null && body.ok() && body.content() != null) {
                md = body.content();
            }
        } catch (RuntimeException ex) {
            log.debug("读取技能说明失败，仅注入名称指令: {}", ex.toString());
        }
        return new ForcedSkill(hit.name(), md);
    }

    private List<Map<String, String>> withGeneratedDocHint(List<Map<String, String>> messages) {
        if (!properties.isAgentRunnerEnabled()) {
            return messages;
        }
        String hint = "若你生成了 xlsx / md / doc / docx 文档，请用 Write 工具写入工作区完整文件（用普通文件名，例如 report.xlsx）。"
                + "不要编造 /QianXunService/data/files/public/ 链接，也不要把 Docker 内部主机名发给用户。"
                + "平台会自动入库，并在对话中追加用户可点击的下载与预览链接。";
        List<Map<String, String>> out = new ArrayList<>(messages.size() + 1);
        out.add(Map.of("role", "system", "content", hint));
        out.addAll(messages);
        return out;
    }

    private static String coalesce(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        return b == null ? "" : b.trim();
    }

    private static String blankOrDefault(String v, String d) {
        if (v == null || v.trim().isEmpty()) {
            return d == null ? "" : d.trim();
        }
        return v.trim();
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private record ChatEndpoint(String baseUrl, String apiKey, String model) {}

    private static class StreamContext {
        private final ChatRun run;
        private final ChatActivityLog.Builder logBuilder;
        private final StringBuilder responseText = new StringBuilder();
        /** 当前会话选用的注册智能体（对话走绑定的 Hermes profile） */
        private Optional<AgentRegistryItem> activeAgent = Optional.empty();
        private String hermesProfile = "";
        private final List<Map<String, Object>> toolCalls = new ArrayList<>();
        private Map<String, Object> usage;
        private List<String> suggestions;
        /** Dashboard：会话仍有活跃长程目标时，message.complete 后需等 goal 续轮 */
        private boolean awaitGoalContinuations;
        private String assistantMessageId = "";
        private long lastDraftFlushAt;

        private final String userId;
        private final long startedAt = System.currentTimeMillis();
        private final java.util.LinkedHashSet<String> ingestedPaths = new java.util.LinkedHashSet<>();

        StreamContext(ChatRun run, ChatActivityLog.Builder logBuilder, String userId) {
            this.run = run;
            this.logBuilder = logBuilder;
            this.userId = userId == null ? "" : userId;
        }
        ChatRun run() { return run; }
        ChatActivityLog.Builder logBuilder() { return logBuilder; }
        String userId() { return userId; }
        long startedAt() { return startedAt; }
        Optional<AgentRegistryItem> activeAgent() { return activeAgent; }
        void setActiveAgent(Optional<AgentRegistryItem> agent) {
            this.activeAgent = agent != null ? agent : Optional.empty();
        }
        String hermesProfile() { return hermesProfile; }
        void setHermesProfile(String profile) {
            this.hermesProfile = profile == null ? "" : profile;
        }
    }

    private int resolveContextWindow(String modelCode, String upstreamModel, String hermesProfile) {
        return contextWindowResolver.resolve(modelCode, upstreamModel, hermesProfile);
    }

    // ── 各阶段拆分方法 ─────────────────────────────────────────────────────────

    private boolean validateConfig() {
        boolean useMock = shouldUseMock();
        if (!useMock) {
            if (properties.isAgentRunnerEnabled()) {
                if (trim(properties.getClaude().getBaseUrl()).isEmpty()) {
                    throw new IllegalStateException("未配置 Claude Code 网关地址（qianxun.claude.base-url）");
                }
            } else {
                ChatEndpoint preview = resolveChatEndpoint(null, "");
                if (preview.baseUrl().isBlank()) {
                    throw new IllegalStateException("未配置 LLM 的 base-url");
                }
                if (preview.apiKey().trim().isEmpty()) {
                    throw new IllegalStateException("缺少 OPENAI_API_KEY（或 qianxun.llm.api-key）");
                }
            }
        }
        return useMock;
    }

    private void callLlmStream(
            StreamContext ctx,
            List<Map<String, String>> llmMessages,
            boolean useMock,
            boolean useDashboard,
            ChatDashboardTurn.Plan dashboardPlan,
            String userContent,
            String modelCode,
            String hermesProfile,
            String sessionId
    ) throws Exception {
        ChatEndpoint endpoint = resolveChatEndpoint(modelCode, hermesProfile);
        ctx.logBuilder().llmEndpoint(endpoint.baseUrl()).llmModel(endpoint.model());
        int contextWindow = resolveContextWindow(modelCode, endpoint.model(), hermesProfile);

        long llmStart = System.currentTimeMillis();
        if (useMock) {
            ctx.logBuilder().status(ChatActivityLog.STATUS_MOCK);
            ctx.logBuilder().llmRequestJson(buildMockRequestNote(userContent));
            openAiClient.streamMockReply(userContent, token ->
                handleToken(ctx, token), ctx.run()::isCancelRequested
            );
        } else if (useDashboard) {
            ctx.logBuilder().llmEndpoint(hermesAgentClient.origin() + "/v1/agent/stream");
            ctx.logBuilder().llmRequestJson(buildLlmRequestJson("claude-code-http", llmMessages));
            OpenAiCompatibleStreamClient.StreamCompletionMeta streamMeta = claudeCodeChatClient.streamTurn(
                    sessionId,
                    ctx.userId(),
                    hermesProfile,
                    dashboardPlan,
                    ctx.awaitGoalContinuations,
                    token -> handleToken(ctx, token),
                    toolCall -> sendToolCallEvent(ctx, toolCall),
                    usage -> sendUsageEvent(ctx, usage, contextWindow),
                    ctx.run()::isCancelRequested,
                    abort -> ctx.run().onInterrupt(abort),
                    (phase, trigger, preTokens) -> sendCompactEvent(ctx, phase, trigger, preTokens),
                    contextWindow
            );
            applyStreamMeta(ctx, streamMeta, llmStart, contextWindow);
        } else {
            String requestJson = buildLlmRequestJson(endpoint.model(), llmMessages);
            ctx.logBuilder().llmRequestJson(requestJson);
            OpenAiCompatibleStreamClient.StreamCompletionMeta streamMeta = openAiClient.streamChatCompletions(
                    endpoint.baseUrl(), endpoint.apiKey(), endpoint.model(), llmMessages,
                    token -> handleToken(ctx, token),
                    toolCall -> sendToolCallEvent(ctx, toolCall),
                    usage -> sendUsageEvent(ctx, usage, contextWindow),
                    ctx.run()::isCancelRequested,
                    closeable -> ctx.run().onInterrupt(() -> {
                        try {
                            closeable.close();
                        } catch (Exception ignored) {
                            /* already closed */
                        }
                    })
            );
            applyStreamMeta(ctx, streamMeta, llmStart, contextWindow);
        }
        try {
            ingestGeneratedDocumentsFromReply(ctx);
            ingestGeneratedDocumentsFromWorkspace(ctx);
        } catch (CancellationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("入库本轮生成文档失败: {}", ex.toString());
        }
        ctx.logBuilder().llmResponseText(ctx.responseText.toString());
    }

    private void applyStreamMeta(
            StreamContext ctx,
            OpenAiCompatibleStreamClient.StreamCompletionMeta streamMeta,
            long llmStart,
            int contextWindow
    ) {
        if (streamMeta.usage() != null && ctx.usage == null) {
            sendUsageEvent(ctx, streamMeta.usage(), contextWindow);
        }
        ctx.logBuilder().llmDurationMs(System.currentTimeMillis() - llmStart);
        if (!streamMeta.sawDone() || "length".equals(streamMeta.finishReason())) {
            String msg = "length".equals(streamMeta.finishReason())
                    ? "回答可能因输出长度上限被截断，可调大环境变量 QIANXUN_LLM_MAX_TOKENS 或 qianxun.llm.max-tokens。"
                    : "上游流式连接在未正常结束时断开，若正文不完整请重试。";
            publish(ctx, "stream_warning", Map.of(
                    "finishReason", streamMeta.finishReason() == null ? "" : streamMeta.finishReason(),
                    "sawDone", streamMeta.sawDone(),
                    "message", msg
            ));
        }
    }

    private void generateAndSendSuggestions(
            StreamContext ctx,
            List<ChatMessage> history,
            String userContent,
            String modelCode,
            boolean useMock
    ) {
        List<String> items;
        try {
            items = useMock
                    ? defaultNextStepSuggestions(userContent)
                    : askLlmForNextStepSuggestions(ctx, history, userContent, modelCode);
        } catch (Exception ex) {
            log.debug("下一步建议生成失败（忽略）: {}", ex.toString());
            items = defaultNextStepSuggestions(userContent);
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        ctx.suggestions = items;
        publish(ctx, "suggestions", Map.of("items", items));
    }

    /**
     * 用会话历史 + 本轮回答，经智能体（Hermes）OpenAI 兼容短请求提取下一步建议。
     * 不绑 profile Dashboard 长对话；优先 hermes.base-url，否则回退 qianxun.llm.*。
     */
    private List<String> askLlmForNextStepSuggestions(
            StreamContext ctx,
            List<ChatMessage> history,
            String userContent,
            String modelCode
    ) throws Exception {
        ChatEndpoint endpoint = resolveSuggestionEndpoint(modelCode);
        String transcript = buildSuggestionTranscript(history, ctx.responseText.toString());
        if (transcript.isBlank()) {
            return defaultNextStepSuggestions(userContent);
        }
        String prompt = """
                你是对话续写助手。请根据下方「会话摘录」（含多轮历史与本轮最新回答），推断用户接下来最可能继续说的 3 条短句。

                要求：
                1. 紧扣摘录中的主题、未完成事项、已给出的结论或可追问点，禁止空泛套话（如「下一步可以怎么做」「有哪些风险」）。
                2. 每条是用户口吻的下一轮输入，可直接发送；不超过 24 个汉字；互不重复。
                3. 优先：澄清细节、要示例/对比、请求落地步骤、追问依据或边界条件。
                4. 只输出 JSON 字符串数组，不要 markdown，不要解释。

                会话摘录：
                %s
                """.formatted(transcript);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "你只输出 JSON 字符串数组，例如 [\"……\",\"……\",\"……\"]。不要工具调用，不要解释。"
        ));
        messages.add(Map.of("role", "user", "content", prompt));
        String raw = openAiClient.completeChat(
                endpoint.baseUrl(), endpoint.apiKey(), endpoint.model(),
                messages, 0.35, java.time.Duration.ofSeconds(20)
        );
        List<String> parsed = parseSuggestionItems(raw);
        return parsed.isEmpty() ? defaultNextStepSuggestions(userContent) : parsed;
    }

    /**
     * 下一步建议专用端点：短请求。智能体长对话走 Claude Code；建议仍用 OpenAI 兼容网关。
     */
    private ChatEndpoint resolveSuggestionEndpoint(String selectedModelCode) {
        QianxunProperties.Llm llm = properties.getLlm();
        String llmBase = trim(llm.getBaseUrl());
        String llmKey = llm.getApiKey() == null ? "" : llm.getApiKey().trim();
        if (!llmBase.isBlank() && !llmKey.isEmpty()) {
            return new ChatEndpoint(llmBase, llmKey, blankOrDefault(llm.getModel(), "gpt-4o-mini"));
        }
        if (properties.isAgentRunnerEnabled()) {
            QianxunProperties.Claude claude = properties.getClaude();
            return new ChatEndpoint(
                    "claude-code",
                    claude.getApiKey(),
                    blankOrDefault(systemSettingsService.resolvedClaudeChatModel(), "claude-sonnet-4-5")
            );
        }
        if (selectedModelCode != null && !selectedModelCode.isBlank()) {
            var selected = modelRegistryRepository.findByCode(selectedModelCode.trim());
            if (selected.isPresent() && selected.get().enabled()) {
                ChatEndpoint fromRegistry = endpointFromRegistryModel(selected.get());
                if (!fromRegistry.baseUrl().isBlank() && !trim(fromRegistry.apiKey()).isEmpty()) {
                    return fromRegistry;
                }
            }
        }
        return resolveChatEndpoint(selectedModelCode, "");
    }

    /** 取最近若干轮 user/assistant，并附上本轮助手回答（尚未落库）。 */
    static String buildSuggestionTranscript(List<ChatMessage> history, String latestAssistant) {
        StringBuilder sb = new StringBuilder();
        List<ChatMessage> turns = new ArrayList<>();
        if (history != null) {
            for (ChatMessage m : history) {
                if (m == null || m.role() == null) {
                    continue;
                }
                if (!"user".equals(m.role()) && !"assistant".equals(m.role())) {
                    continue;
                }
                if (m.content() == null || m.content().isBlank()) {
                    continue;
                }
                turns.add(m);
            }
        }
        int from = Math.max(0, turns.size() - 8);
        for (int i = from; i < turns.size(); i++) {
            ChatMessage m = turns.get(i);
            appendSuggestionTurn(sb, m.role(), m.content(), "user".equals(m.role()) ? 320 : 900);
        }
        if (latestAssistant != null && !latestAssistant.isBlank()) {
            appendSuggestionTurn(sb, "assistant", latestAssistant, 1200);
        }
        String out = sb.toString().trim();
        if (out.length() > 4500) {
            out = out.substring(out.length() - 4500);
        }
        return out;
    }

    private static void appendSuggestionTurn(StringBuilder sb, String role, String content, int maxChars) {
        String label = "user".equals(role) ? "用户" : "助手";
        String body = content.replaceAll("\\s+", " ").trim();
        if (body.length() > maxChars) {
            body = body.substring(0, maxChars) + "…";
        }
        sb.append(label).append('：').append(body).append('\n');
    }

    private List<String> parseSuggestionItems(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = raw.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            JsonNode arr = objectMapper.readTree(text);
            if (!arr.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode n : arr) {
                if (!n.isTextual()) {
                    continue;
                }
                String item = n.asText("").replaceAll("\\s+", " ").trim();
                if (item.isEmpty()) {
                    continue;
                }
                if (item.length() > 40) {
                    item = item.substring(0, 40);
                }
                if (!out.contains(item)) {
                    out.add(item);
                }
                if (out.size() >= 3) {
                    break;
                }
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static List<String> defaultNextStepSuggestions(String userContent) {
        List<String> items = new ArrayList<>();
        String topic = "";
        if (userContent != null && !userContent.isBlank()) {
            topic = userContent.replaceAll("\\s+", " ").trim();
            topic = topic.replaceFirst("^(请|帮我|麻烦|我想|我要|如何|怎么|怎样)", "").trim();
            if (topic.length() > 12) {
                topic = topic.substring(0, 12);
            }
        }
        if (topic.length() >= 4) {
            items.add("结合「" + topic + "」给出可执行步骤");
            items.add("针对「" + topic + "」补充依据或示例");
            items.add("「" + topic + "」还有哪些边界或注意点？");
        } else {
            items.add("请按优先级列出可执行步骤");
            items.add("请补充关键依据或具体示例");
            items.add("还有哪些边界条件或注意点？");
        }
        return items.size() > 3 ? items.subList(0, 3) : items;
    }

    private static boolean isSubagentRow(Map<String, Object> row) {
        if (row == null) {
            return false;
        }
        Object flag = row.get("subagent");
        if (Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag))) {
            return true;
        }
        String n = String.valueOf(row.getOrDefault("toolName", "")).trim().toLowerCase();
        return n.equals("subagent") || n.equals("agent") || n.equals("task") || n.contains("delegate");
    }

    private static void applyIncomingStatus(Map<String, Object> row, String incomingRaw) {
        String incoming = incomingRaw.trim().toLowerCase();
        String current = String.valueOf(row.getOrDefault("status", "")).trim().toLowerCase();
        boolean currentTerminal = "completed".equals(current) || "error".equals(current);
        boolean incomingProgress = "running".equals(incoming) || "started".equals(incoming);
        if (currentTerminal && incomingProgress) {
            if (isSubagentRow(row)) {
                row.put("status", incomingRaw);
                row.remove("endedAt");
                row.remove("durationMs");
            }
            return;
        }
        if (("awaiting".equals(current) || "background".equals(current)) && incomingProgress) {
            return;
        }
        row.put("status", incomingRaw);
    }

    private static boolean hasAwaitingAncestor(StreamContext ctx, Map<String, Object> row) {
        String pid = String.valueOf(row.getOrDefault("parentId", "")).trim();
        int guard = 0;
        while (!pid.isBlank() && guard++ < 32) {
            Map<String, Object> parent = null;
            for (Map<String, Object> existing : ctx.toolCalls) {
                if (pid.equals(String.valueOf(existing.getOrDefault("toolCallId", "")))) {
                    parent = existing;
                    break;
                }
            }
            if (parent == null) {
                return false;
            }
            String st = String.valueOf(parent.getOrDefault("status", "")).toLowerCase();
            if ("awaiting".equals(st) || "background".equals(st)) {
                return true;
            }
            pid = String.valueOf(parent.getOrDefault("parentId", "")).trim();
        }
        return false;
    }

    private static boolean isDescendantOf(StreamContext ctx, Map<String, Object> row, String ancestorId) {
        if (ancestorId == null || ancestorId.isBlank()) {
            return false;
        }
        String pid = String.valueOf(row.getOrDefault("parentId", "")).trim();
        int guard = 0;
        while (!pid.isBlank() && guard++ < 32) {
            if (ancestorId.equals(pid)) {
                return true;
            }
            String next = "";
            for (Map<String, Object> existing : ctx.toolCalls) {
                if (pid.equals(String.valueOf(existing.getOrDefault("toolCallId", "")))) {
                    next = String.valueOf(existing.getOrDefault("parentId", "")).trim();
                    break;
                }
            }
            pid = next;
        }
        return false;
    }

    private static void closeDescendantToolCalls(StreamContext ctx, String parentId, String terminalStatus) {
        if (parentId == null || parentId.isBlank() || ctx.toolCalls.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map<String, Object> row : ctx.toolCalls) {
            if (!isDescendantOf(ctx, row, parentId)) {
                continue;
            }
            String status = String.valueOf(row.getOrDefault("status", "")).trim().toLowerCase();
            if ("completed".equals(status) || "error".equals(status)
                    || "awaiting".equals(status) || "background".equals(status)) {
                continue;
            }
            row.put("status", terminalStatus);
            if (row.get("endedAt") == null) {
                row.put("endedAt", now);
            }
        }
    }

    private static void closeOpenToolCalls(StreamContext ctx, String terminalStatus) {
        if (ctx.toolCalls.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map<String, Object> row : ctx.toolCalls) {
            String status = String.valueOf(row.getOrDefault("status", "")).trim().toLowerCase();
            if ("completed".equals(status) || "error".equals(status)
                    || "awaiting".equals(status) || "background".equals(status)
                    || hasAwaitingAncestor(ctx, row)) {
                continue;
            }
            row.put("status", terminalStatus);
            if (row.get("endedAt") == null) {
                row.put("endedAt", now);
            }
            Object started = row.get("startedAt");
            Object ended = row.get("endedAt");
            if (started instanceof Number s && ended instanceof Number e) {
                row.put("durationMs", Math.max(0, e.longValue() - s.longValue()));
            }
        }
    }

    private void saveAndComplete(StreamContext ctx) {
        closeOpenToolCalls(ctx, "completed");
        String sessionId = ctx.logBuilder().build().sessionId();
        String answer    = ctx.responseText.toString();
        ctx.logBuilder().llmResponseText(answer);
        String toolCallsJson = null;
        String usageJson = null;
        String suggestionsJson = null;
        try {
            if (!ctx.toolCalls.isEmpty()) {
                toolCallsJson = objectMapper.writeValueAsString(ctx.toolCalls);
            }
            if (ctx.usage != null && !ctx.usage.isEmpty()) {
                usageJson = objectMapper.writeValueAsString(ctx.usage);
            }
            if (ctx.suggestions != null && !ctx.suggestions.isEmpty()) {
                suggestionsJson = objectMapper.writeValueAsString(ctx.suggestions);
            }
        } catch (Exception ex) {
            log.debug("tool/usage/suggestions JSON 序列化失败（忽略）: {}", ex.toString());
        }
        String assistantId = persistFinalAssistant(ctx, answer, toolCallsJson, usageJson, suggestionsJson, ChatMessage.STATUS_COMPLETED);
        ctx.logBuilder().assistantMessageId(assistantId)
                        .totalDurationMs(System.currentTimeMillis());
        activityLogService.saveLog(ctx.logBuilder().build());
        publishTerminal(ctx, "done", Map.of(
                "assistantMessageId", assistantId,
                "sessionId", sessionId
        ));
        activeRunRegistry.finish(ctx.run(), ChatRun.Status.COMPLETED);
    }

    private String persistFinalAssistant(
            StreamContext ctx,
            String answer,
            String toolCallsJson,
            String usageJson,
            String suggestionsJson,
            String status
    ) {
        if (ctx.assistantMessageId != null && !ctx.assistantMessageId.isBlank()) {
            try {
                sessionService.updateAssistantMessage(
                        ctx.assistantMessageId, answer, toolCallsJson, usageJson, suggestionsJson, status
                );
                return ctx.assistantMessageId;
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                log.warn("助手消息更新过大，压缩 tool_calls 后重试: {}", ex.getMostSpecificCause().getMessage());
                String compacted = compactToolCallsJson(toolCallsJson);
                try {
                    sessionService.updateAssistantMessage(
                            ctx.assistantMessageId, answer, compacted, usageJson, suggestionsJson, status
                    );
                    return ctx.assistantMessageId;
                } catch (org.springframework.dao.DataIntegrityViolationException ex2) {
                    log.warn("压缩后仍过大，去掉 tool_calls 仅保存正文: {}", ex2.getMostSpecificCause().getMessage());
                    sessionService.updateAssistantMessage(
                            ctx.assistantMessageId, answer, null, usageJson, suggestionsJson, status
                    );
                    return ctx.assistantMessageId;
                }
            }
        }
        return persistAssistantMessage(
                ctx.logBuilder().build().sessionId(), answer, toolCallsJson, usageJson, suggestionsJson
        ).id();
    }

    /**
     * 落库失败（常见：tool_calls 超 TEXT 上限）时压缩重试，避免整轮流在已输出后被当成中断。
     */
    private com.qianxun.domain.ChatMessage persistAssistantMessage(
            String sessionId,
            String answer,
            String toolCallsJson,
            String usageJson,
            String suggestionsJson
    ) {
        try {
            return sessionService.appendAssistantMessage(
                    sessionId, answer, toolCallsJson, usageJson, suggestionsJson
            );
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.warn("助手消息落库过大，压缩 tool_calls 后重试: {}", ex.getMostSpecificCause().getMessage());
            String compacted = compactToolCallsJson(toolCallsJson);
            try {
                return sessionService.appendAssistantMessage(
                        sessionId, answer, compacted, usageJson, suggestionsJson
                );
            } catch (org.springframework.dao.DataIntegrityViolationException ex2) {
                log.warn("压缩后仍过大，去掉 tool_calls 仅保存正文: {}", ex2.getMostSpecificCause().getMessage());
                return sessionService.appendAssistantMessage(
                        sessionId, answer, null, usageJson, suggestionsJson
                );
            }
        }
    }

    private String compactToolCallsJson(String toolCallsJson) {
        if (toolCallsJson == null || toolCallsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(toolCallsJson);
            if (!root.isArray()) {
                return toolCallsJson.length() > 48_000 ? toolCallsJson.substring(0, 48_000) : toolCallsJson;
            }
            for (JsonNode node : root) {
                if (!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode obj)) {
                    continue;
                }
                for (String key : List.of("result", "args", "stderr", "inlineDiff", "resultText", "summary", "context")) {
                    JsonNode v = obj.get(key);
                    if (v != null && v.isTextual() && v.asText().length() > 4_000) {
                        obj.put(key, v.asText().substring(0, 4_000) + "…(已截断)");
                    }
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            log.debug("compact tool_calls failed: {}", ex.toString());
            return toolCallsJson.length() > 48_000 ? toolCallsJson.substring(0, 48_000) + "…" : toolCallsJson;
        }
    }

    private void handleStreamCancelled(StreamContext ctx, long totalStart) {
        log.info("流式问答已取消 session={} run={}",
                ctx.logBuilder().build().sessionId(), ctx.run().runId());
        String answer = ctx.responseText.toString();
        ctx.logBuilder().llmResponseText(answer);
        String assistantId = "";
        try {
            assistantId = persistFinalAssistant(ctx, answer, null, null, null, ChatMessage.STATUS_CANCELLED);
            ctx.logBuilder().assistantMessageId(assistantId);
        } catch (Exception ex) {
            log.warn("取消时落库助手草稿失败: {}", ex.toString());
        }
        activityLogService.saveLog(
                ctx.logBuilder().status(ChatActivityLog.STATUS_ERROR)
                        .errorMessage("cancelled")
                        .totalDurationMs(System.currentTimeMillis() - totalStart)
                        .build()
        );
        try {
            publishTerminal(ctx, "done", Map.of(
                    "assistantMessageId", assistantId == null ? "" : assistantId,
                    "sessionId", ctx.logBuilder().build().sessionId(),
                    "cancelled", true
            ));
        } catch (Exception ignored) {
            /* ignore */
        }
        activeRunRegistry.finish(ctx.run(), ChatRun.Status.CANCELLED);
    }

    private void handleStreamError(StreamContext ctx, long totalStart, Throwable ex) {
        log.warn("流式问答失败: {}", ex.toString());
        String answer = ctx.responseText.toString();
        ctx.logBuilder().llmResponseText(answer);
        try {
            if (ctx.assistantMessageId != null && !ctx.assistantMessageId.isBlank()) {
                persistFinalAssistant(ctx, answer, null, null, null, ChatMessage.STATUS_ERROR);
            }
        } catch (Exception persistEx) {
            log.warn("错误路径落库助手草稿失败: {}", persistEx.toString());
        }
        activityLogService.saveLog(
                ctx.logBuilder().status(ChatActivityLog.STATUS_ERROR)
                                  .errorMessage(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                                  .totalDurationMs(System.currentTimeMillis() - totalStart)
                                  .build()
        );
        try {
            publishTerminal(ctx, "error", Map.of("message", ex.getMessage() == null ? "unknown" : ex.getMessage()));
        } catch (Exception ignored) {
            /* 客户端可能已断开 */
        }
        activeRunRegistry.finish(ctx.run(), ChatRun.Status.FAILED);
    }
}
