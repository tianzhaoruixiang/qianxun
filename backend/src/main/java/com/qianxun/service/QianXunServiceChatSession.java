package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.domain.ChatMessage;
import com.qianxun.domain.ChatSession;
import com.qianxun.repo.ChatMessageRepository;
import com.qianxun.repo.ChatSessionRepository;
import com.qianxun.web.dto.ChatMessageResponse;
import com.qianxun.web.dto.ChatSessionResponse;
import com.qianxun.web.dto.CreateSessionRequest;
import com.qianxun.web.dto.UpdateSessionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QianXunServiceChatSession {

    private static final int MAX_LIST     = 500;
    private static final int MAX_MESSAGES = 500;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final QianXunServiceActivityLog activityLogService;

    public QianXunServiceChatSession(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            QianXunServiceActivityLog activityLogService
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public ChatSessionResponse create(CreateSessionRequest request) {
        String userId = UserContext.getCurrentUserId();
        Instant now   = Instant.now();
        String id     = newId();
        String title  = request == null || request.title() == null || request.title().isBlank()
                ? "新会话"
                : request.title().trim();
        ChatSession session = new ChatSession(id, userId, title, now, now);
        sessionRepository.insert(session);
        return toResponse(session);
    }

    public List<ChatSessionResponse> list() {
        String userId = UserContext.getCurrentUserId();
        return sessionRepository.listByUserIdOrderByUpdatedDesc(userId, MAX_LIST)
                .stream().map(QianXunServiceChatSession::toResponse).toList();
    }

    public ChatSessionResponse get(String id) {
        String userId = UserContext.getCurrentUserId();
        return sessionRepository.findByIdAndUserId(id, userId)
                .map(QianXunServiceChatSession::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    @Transactional
    public ChatSessionResponse update(String id, UpdateSessionRequest request) {
        String userId = UserContext.getCurrentUserId();
        sessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        if (request == null || request.title() == null || request.title().isBlank()) {
            return sessionRepository.findByIdAndUserId(id, userId)
                    .map(QianXunServiceChatSession::toResponse).orElseThrow();
        }
        Instant now = Instant.now();
        sessionRepository.updateTitle(id, userId, request.title().trim(), now);
        return sessionRepository.findByIdAndUserId(id, userId)
                .map(QianXunServiceChatSession::toResponse).orElseThrow();
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

    /** 内部：不验证 userId，仅检查存在性（用于 SSE 线程内操作） */
    public void ensureSession(String sessionId) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    @Transactional
    public ChatMessage appendUserMessage(String sessionId, String content) {
        ensureSession(sessionId);
        Instant now = Instant.now();
        String id   = newId();
        ChatMessage message = new ChatMessage(
                id, sessionId, "user", content, ChatMessage.MODE_QUICK, null, null, now
        );
        messageRepository.insert(message);
        sessionRepository.touchUpdatedAt(sessionId, now);
        maybeRenameSessionFromFirstQuestion(sessionId, content);
        return message;
    }

    @Transactional
    public ChatMessage appendAssistantMessage(
            String sessionId, String content, String thinkingMode, String thinkContent, String entityCardsJson
    ) {
        Instant now = Instant.now();
        String id   = newId();
        ChatMessage message = new ChatMessage(
                id, sessionId, "assistant", content, thinkingMode, thinkContent, entityCardsJson, now
        );
        messageRepository.insert(message);
        sessionRepository.touchUpdatedAt(sessionId, now);
        return message;
    }

    public List<ChatMessage> loadHistoryForLlm(String sessionId) {
        return messageRepository.listBySessionOrderByCreatedAsc(sessionId, MAX_MESSAGES);
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

    private static ChatSessionResponse toResponse(ChatSession s) {
        return new ChatSessionResponse(s.id(), s.title(), s.createdAt(), s.updatedAt());
    }

    private static ChatMessageResponse toMessageResponse(ChatMessage m) {
        String entityJson = "assistant".equals(m.role()) ? m.entityCardsJson() : null;
        if (entityJson != null && entityJson.isBlank()) {
            entityJson = null;
        }
        return new ChatMessageResponse(
                m.id(), m.sessionId(), m.role(), m.content(),
                m.thinkingMode(), m.thinkContent(), entityJson, m.createdAt()
        );
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
