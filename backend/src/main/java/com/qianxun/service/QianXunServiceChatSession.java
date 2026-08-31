package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.ChatMessage;
import com.qianxun.domain.ChatSession;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.ChatMessageRepository;
import com.qianxun.repo.ChatSessionRepository;
import com.qianxun.service.stream.ActiveRunRegistry;
import com.qianxun.service.stream.ChatRun;
import com.qianxun.web.dto.ChatMessageResponse;
import com.qianxun.web.dto.ChatSessionListResponse;
import com.qianxun.web.dto.ChatSessionResponse;
import com.qianxun.web.dto.CreateSessionRequest;
import com.qianxun.web.dto.ListSessionsRequest;
import com.qianxun.web.dto.SessionAgentFacet;
import com.qianxun.web.dto.SessionGoalResponse;
import com.qianxun.web.dto.UpdateSessionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QianXunServiceChatSession {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_LIST          = 500;
    /** 单会话加载上限：最近 N 条（见 {@link ChatMessageRepository#listBySessionOrderByCreatedAsc}） */
    private static final int MAX_MESSAGES      = 500;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final QianXunServiceActivityLog activityLogService;
    private final AgentRegistryRepository agentRegistryRepository;
    private final ActiveRunRegistry activeRunRegistry;

    public QianXunServiceChatSession(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            QianXunServiceActivityLog activityLogService,
            AgentRegistryRepository agentRegistryRepository,
            ActiveRunRegistry activeRunRegistry
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.activityLogService = activityLogService;
        this.agentRegistryRepository = agentRegistryRepository;
        this.activeRunRegistry = activeRunRegistry;
    }

    @Transactional
    public ChatSessionResponse create(CreateSessionRequest request) {
        String userId = UserContext.getCurrentUserId();
        Instant now   = Instant.now();
        String id     = newId();
        String title  = request == null || request.title() == null || request.title().isBlank()
                ? "新会话"
                : request.title().trim();
        String agentCode = request == null ? "" : trim(request.agentCode());
        String hermesProfile = request == null ? "" : trim(request.hermesProfile());
        if (hermesProfile.isEmpty() && !agentCode.isEmpty()) {
            hermesProfile = agentRegistryRepository.findByCode(agentCode)
                    .map(a -> trim(a.hermesProfile()))
                    .orElse("");
        }
        String requestedName = request == null ? "" : trim(request.agentName());
        String registryName = lookupRegistryName(agentCode, hermesProfile);
        String agentName = SessionAgentLabels.snapshotName(agentCode, hermesProfile, requestedName, registryName);
        ChatSession session = new ChatSession(id, userId, title, now, now, agentCode, hermesProfile, agentName);
        sessionRepository.insert(session);
        return toResponse(session, null, null);
    }

    public ChatSessionListResponse list(ListSessionsRequest request) {
        String userId = UserContext.getCurrentUserId();
        int limit = resolveLimit(request);
        Instant cursorUpdatedAt = parseCursorTime(request);
        String cursorId = request == null ? "" : trim(request.cursorId());
        boolean useCursor = cursorUpdatedAt != null && !cursorId.isEmpty();
        int offset = useCursor ? 0 : resolveOffset(request, limit);
        int page = useCursor ? 1 : (limit <= 0 ? 1 : offset / limit + 1);
        ChatSessionRepository.SessionListFilter filter = new ChatSessionRepository.SessionListFilter(
                request == null ? "" : trim(request.keyword()),
                request == null ? "" : trim(request.agentGroup()),
                useCursor ? cursorUpdatedAt : null,
                useCursor ? cursorId : ""
        );
        List<ChatSessionRepository.ChatSessionWithStats> rows =
                sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(userId, limit + 1, offset, filter);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        List<ChatSessionResponse> items = rows.stream()
                .map(row -> toResponse(
                        new ChatSession(
                                row.id(), row.userId(), row.title(), row.createdAt(), row.updatedAt(),
                                row.agentCode(), row.hermesProfile(), row.agentName(), row.sessionGoal()
                        ),
                        clampCount(row.messageCount()),
                        preview(row.lastMessagePreview())
                ).withStreaming(activeRunRegistry.isStreaming(row.id())))
                .toList();
        return new ChatSessionListResponse(
                items, page, limit, offset, hasMore, useCursor ? List.of() : listAgentFacets(userId)
        );
    }

    static int resolveLimit(ListSessionsRequest request) {
        int limit = request == null || request.limit() == null ? DEFAULT_PAGE_SIZE : request.limit();
        if (limit < 1) {
            limit = DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_LIST);
    }

    static int resolveOffset(ListSessionsRequest request, int limit) {
        if (request != null && request.offset() != null) {
            return Math.max(0, request.offset());
        }
        int page = request == null || request.page() == null ? 1 : request.page();
        if (page < 1) {
            page = 1;
        }
        long raw = (long) (page - 1) * (long) limit;
        if (raw > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) raw;
    }

    private static Instant parseCursorTime(ListSessionsRequest request) {
        if (request == null || request.cursorUpdatedAt() == null || request.cursorUpdatedAt().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(request.cursorUpdatedAt().trim()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private List<SessionAgentFacet> listAgentFacets(String userId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("__digital_officer__", SessionAgentLabels.DIGITAL_OFFICER);
        for (ChatSessionRepository.AgentFacetRow row : sessionRepository.listAgentFacetsByUserId(userId)) {
            String key = agentGroupKey(row.agentCode(), row.hermesProfile(), row.agentName());
            String label = SessionAgentLabels.displayName(
                    row.agentCode(), row.hermesProfile(), row.agentName(), lookupRegistryName(row.agentCode(), row.hermesProfile())
            );
            map.putIfAbsent(key, label);
        }
        List<SessionAgentFacet> facets = new ArrayList<>();
        for (var e : map.entrySet()) {
            facets.add(new SessionAgentFacet(e.getKey(), e.getValue()));
        }
        return facets;
    }

    static String agentGroupKey(String agentCode, String hermesProfile, String agentName) {
        String code = trim(agentCode);
        if (!code.isEmpty()) {
            return "code:" + code;
        }
        String name = trim(agentName);
        if (SessionAgentLabels.DIGITAL_OFFICER.equals(name) || SessionAgentLabels.isDefaultProfile(hermesProfile)) {
            return "__digital_officer__";
        }
        if (SessionAgentLabels.UNCATEGORIZED.equals(name)) {
            return "uncat";
        }
        String profile = trim(hermesProfile);
        if (!profile.isEmpty()) {
            return "profile:" + profile.toLowerCase();
        }
        return "__digital_officer__";
    }

    public ChatSessionResponse get(String id) {
        String userId = UserContext.getCurrentUserId();
        return sessionRepository.findByIdAndUserId(id, userId)
                .map(s -> toResponse(s, null, null))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    @Transactional
    public ChatSessionResponse update(String id, UpdateSessionRequest request) {
        String userId = UserContext.getCurrentUserId();
        sessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        Instant now = Instant.now();
        if (request != null && request.title() != null && !request.title().isBlank()) {
            sessionRepository.updateTitle(id, userId, request.title().trim(), now);
        }
        if (request != null && Boolean.TRUE.equals(request.clearGoal())) {
            sessionRepository.updateSessionGoal(id, userId, "", now);
        } else if (request != null && request.goal() != null) {
            ChatGoalInvocation.Goal goal = ChatGoalInvocation.fromRequest(request.goal());
            if (goal.isBlank() || goal.title().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写目标标题");
            }
            sessionRepository.updateSessionGoal(id, userId, ChatGoalInvocation.toJson(goal), now);
        }
        return sessionRepository.findByIdAndUserId(id, userId)
                .map(s -> toResponse(s, null, null)).orElseThrow();
    }

    @Transactional
    public void delete(String id) {
        String userId = UserContext.getCurrentUserId();
        if (sessionRepository.findByIdAndUserId(id, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        messageRepository.deleteBySessionId(id);
        sessionRepository.deleteById(id, userId);
        activityLogService.deleteBySession(id);
    }

    /**
     * 删除某智能体下的全部会话（含消息与活动日志）。管理员删除智能体时调用。
     */
    @Transactional
    public int deleteByAgent(String agentCode, String hermesProfile) {
        List<String> ids = sessionRepository.listIdsByAgent(agentCode, hermesProfile);
        if (ids.isEmpty()) {
            return 0;
        }
        for (String id : ids) {
            activeRunRegistry.findRunning(id).ifPresent(ChatRun::requestCancel);
            messageRepository.deleteBySessionId(id);
            activityLogService.deleteBySession(id);
        }
        return sessionRepository.deleteByIds(ids);
    }

    public List<ChatMessageResponse> listMessages(String sessionId) {
        // 验证会话归属
        get(sessionId);
        return messageRepository.listBySessionOrderByCreatedAsc(sessionId, MAX_MESSAGES)
                .stream().map(QianXunServiceChatSession::toMessageResponse).toList();
    }

    // ── 内部接口（不经过 UserContext，供 QianXunServiceChatStream 等内部逻辑使用）────────

    /**
     * SSE 流开始前由 Controller 在请求线程调用，验证会话归属后才提交到 SSE 线程。
     */
    public void ensureSessionOwnership(String sessionId, String userId) {
        sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "无权访问该会话或会话不存在"));
    }

    /** 已归属校验后的会话快照；不存在则 null。 */
    public ChatSession findOwnedOrNull(String sessionId, String userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId).orElse(null);
    }

    /** 内部：不验证 userId，仅检查存在性（用于 SSE 线程内操作） */
    public void ensureSession(String sessionId) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    public void insertInternal(ChatSession session) {
        if (session == null) {
            return;
        }
        sessionRepository.insert(session);
    }

    /**
     * 首轮对话写入当前智能体；已有绑定则跳过。
     */
    public void bindAgentIfEmpty(
            String sessionId,
            String userId,
            String agentCode,
            String hermesProfile,
            String requestedName
    ) {
        String code = trim(agentCode);
        String profile = trim(hermesProfile);
        String registryName = lookupRegistryName(code, profile);
        String agentName = SessionAgentLabels.snapshotName(code, profile, requestedName, registryName);
        sessionRepository.bindAgentIfEmpty(sessionId, userId, code, profile, agentName);
    }

    public ChatGoalInvocation.Goal loadGoal(String sessionId) {
        return sessionRepository.findById(sessionId)
                .map(s -> ChatGoalInvocation.parseJson(s.sessionGoal()))
                .orElse(ChatGoalInvocation.parseJson(""));
    }

    public ChatGoalInvocation.Goal persistGoal(String sessionId, String userId, ChatGoalInvocation.Goal goal) {
        ChatGoalInvocation.Goal clipped = ChatGoalInvocation.clip(goal);
        sessionRepository.updateSessionGoal(sessionId, userId, ChatGoalInvocation.toJson(clipped), Instant.now());
        return clipped;
    }

    public void clearGoal(String sessionId, String userId) {
        sessionRepository.updateSessionGoal(sessionId, userId, "", Instant.now());
    }

    @Transactional
    public ChatMessage appendUserMessage(String sessionId, String content) {
        ensureSession(sessionId);
        Instant now = Instant.now();
        String id   = newId();
        ChatMessage message = new ChatMessage(
                id, sessionId, "user", content, null, null, null, null, null, null, null, now,
                ChatMessage.STATUS_COMPLETED, null
        );
        messageRepository.insert(message);
        sessionRepository.touchUpdatedAt(sessionId, now);
        maybeRenameSessionFromFirstQuestion(sessionId, content);
        return message;
    }

    @Transactional
    public ChatMessage appendAssistantMessage(
            String sessionId,
            String content,
            String toolCallsJson,
            String usageJson,
            String suggestionsJson
    ) {
        return appendAssistantMessage(
                sessionId, content, toolCallsJson, usageJson, suggestionsJson,
                ChatMessage.STATUS_COMPLETED, null
        );
    }

    @Transactional
    public ChatMessage appendAssistantMessage(
            String sessionId,
            String content,
            String toolCallsJson,
            String usageJson,
            String suggestionsJson,
            String status,
            String runId
    ) {
        return appendAssistantMessage(
                sessionId, content, toolCallsJson, usageJson, suggestionsJson, status, runId, null
        );
    }

    @Transactional
    public ChatMessage appendAssistantMessage(
            String sessionId,
            String content,
            String toolCallsJson,
            String usageJson,
            String suggestionsJson,
            String status,
            String runId,
            Instant createdAt
    ) {
        Instant now = createdAt != null ? createdAt : Instant.now();
        String id   = newId();
        ChatMessage message = new ChatMessage(
                id, sessionId, "assistant", content, null, null,
                null, null, toolCallsJson, usageJson, suggestionsJson, now,
                status == null || status.isBlank() ? ChatMessage.STATUS_COMPLETED : status,
                runId
        );
        messageRepository.insert(message);
        sessionRepository.touchUpdatedAt(sessionId, Instant.now());
        return message;
    }

    @Transactional
    public void updateAssistantMessage(
            String messageId,
            String content,
            String toolCallsJson,
            String usageJson,
            String suggestionsJson,
            String status
    ) {
        messageRepository.updateAssistantContent(
                messageId, content, toolCallsJson, usageJson, suggestionsJson, status
        );
    }

    public List<ChatMessage> loadHistoryForLlm(String sessionId) {
        return messageRepository.listBySessionOrderByCreatedAsc(sessionId, MAX_MESSAGES).stream()
                .filter(m -> !m.isStreaming())
                .toList();
    }

    private ChatSessionResponse toResponse(ChatSession s, Integer messageCount, String lastPreview) {
        String registryName = lookupRegistryName(s.agentCode(), s.hermesProfile());
        String name = SessionAgentLabels.displayName(s.agentCode(), s.hermesProfile(), s.agentName(), registryName);
        return new ChatSessionResponse(
                s.id(),
                s.title(),
                s.createdAt(),
                s.updatedAt(),
                messageCount,
                lastPreview,
                blankToNull(s.agentCode()),
                blankToNull(s.hermesProfile()),
                name,
                SessionGoalResponse.from(ChatGoalInvocation.parseJson(s.sessionGoal())),
                null
        );
    }

    private String lookupRegistryName(String agentCode, String hermesProfile) {
        if (agentCode != null && !agentCode.isBlank()) {
            Optional<AgentRegistryItem> byCode = agentRegistryRepository.findByCode(agentCode.trim());
            if (byCode.isPresent() && byCode.get().name() != null && !byCode.get().name().isBlank()) {
                return byCode.get().name();
            }
        }
        if (hermesProfile != null && !hermesProfile.isBlank() && !SessionAgentLabels.isDefaultProfile(hermesProfile)) {
            Optional<AgentRegistryItem> byProfile = agentRegistryRepository.findByHermesProfile(hermesProfile);
            if (byProfile.isPresent() && byProfile.get().name() != null && !byProfile.get().name().isBlank()) {
                return byProfile.get().name();
            }
        }
        return "";
    }

    private void maybeRenameSessionFromFirstQuestion(String sessionId, String userContent) {
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !"新会话".equals(session.title())) { return; }
        String snippet = userContent.replace("\r", " ").replace("\n", " ").trim();
        if (snippet.length() > 48) { snippet = snippet.substring(0, 48) + "…"; }
        if (!snippet.isBlank()) {
            sessionRepository.updateTitle(sessionId, session.userId(), snippet, Instant.now());
        }
    }

    private static ChatMessageResponse toMessageResponse(ChatMessage m) {
        String toolJson = "assistant".equals(m.role()) ? m.toolCallsJson() : null;
        if (toolJson != null && toolJson.isBlank()) {
            toolJson = null;
        }
        String usageJson = "assistant".equals(m.role()) ? m.usageJson() : null;
        if (usageJson != null && usageJson.isBlank()) {
            usageJson = null;
        }
        String suggestionsJson = "assistant".equals(m.role()) ? m.suggestionsJson() : null;
        if (suggestionsJson != null && suggestionsJson.isBlank()) {
            suggestionsJson = null;
        }
        return new ChatMessageResponse(
                m.id(), m.sessionId(), m.role(), m.content(),
                toolJson, usageJson, suggestionsJson, m.createdAt(),
                m.normalizedStatus(),
                blankToNull(m.runId())
        );
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }

    private static int clampCount(long c) {
        if (c > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) c;
    }

    private static String preview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String t = content.replace('\r', ' ').replace('\n', ' ').trim();
        if (t.length() > 120) {
            return t.substring(0, 120) + "…";
        }
        return t;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
