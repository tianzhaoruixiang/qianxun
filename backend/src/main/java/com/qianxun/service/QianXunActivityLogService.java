package com.qianxun.service;

import com.qianxun.domain.ChatActivityLog;
import com.qianxun.domain.MessageFeedback;
import com.qianxun.repo.ChatActivityLogRepository;
import com.qianxun.repo.MessageFeedbackRepository;
import com.qianxun.web.dto.ActivityLogResponse;
import com.qianxun.web.dto.FeedbackResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QianXunActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(QianXunActivityLogService.class);
    private static final int MAX_LOGS = 200;

    private final ChatActivityLogRepository logRepo;
    private final MessageFeedbackRepository feedbackRepo;

    public QianXunActivityLogService(
            ChatActivityLogRepository logRepo,
            MessageFeedbackRepository feedbackRepo
    ) {
        this.logRepo = logRepo;
        this.feedbackRepo = feedbackRepo;
    }

    /** 异步友好：写失败只打日志，不影响流式响应主流程 */
    public void saveLog(ChatActivityLog activityLog) {
        try {
            logRepo.insert(activityLog);
        } catch (Exception ex) {
            log.warn("保存活动日志失败（忽略）: {}", ex.toString());
        }
    }

    public List<ActivityLogResponse> listBySession(String sessionId) {
        return logRepo.listBySessionId(sessionId, MAX_LOGS)
                .stream()
                .map(QianXunActivityLogService::toResponse)
                .toList();
    }

    public Optional<ActivityLogResponse> findByAssistantMessage(String assistantMessageId) {
        return logRepo.findByAssistantMessageId(assistantMessageId).map(QianXunActivityLogService::toResponse);
    }

    // ── 反馈 ──────────────────────────────────────────────────────────────

    /** 提交点赞/点踩；同一消息已有反馈则覆盖（先删后插） */
    public FeedbackResponse submitFeedback(
            String userId,
            String sessionId,
            String messageId,
            String feedbackType,
            String feedbackNote
    ) {
        if (!MessageFeedback.TYPE_LIKE.equals(feedbackType)
                && !MessageFeedback.TYPE_DISLIKE.equals(feedbackType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "feedbackType 只允许 like / dislike");
        }

        // 找到关联的 activity log id
        String activityLogId = logRepo.findByAssistantMessageId(messageId)
                .map(ChatActivityLog::id)
                .orElse(null);

        // 覆盖：先删旧反馈
        feedbackRepo.deleteByMessageId(messageId);

        MessageFeedback feedback = new MessageFeedback(
                UUID.randomUUID().toString().replace("-", ""),
                userId, sessionId, messageId, activityLogId,
                feedbackType, feedbackNote, Instant.now()
        );
        feedbackRepo.insert(feedback);
        return toFeedbackResponse(feedback);
    }

    public Optional<FeedbackResponse> getFeedback(String messageId) {
        return feedbackRepo.findByMessageId(messageId).map(QianXunActivityLogService::toFeedbackResponse);
    }

    public void deleteFeedback(String messageId) {
        feedbackRepo.deleteByMessageId(messageId);
    }

    // ── 级联删除（会话删除时调用）────────────────────────────────────────

    public void deleteBySession(String sessionId) {
        try {
            feedbackRepo.deleteBySessionId(sessionId);
            logRepo.deleteBySessionId(sessionId);
        } catch (Exception ex) {
            log.warn("级联删除活动日志失败（忽略）: {}", ex.toString());
        }
    }

    // ── 转换 ──────────────────────────────────────────────────────────────

    private static ActivityLogResponse toResponse(ChatActivityLog l) {
        return new ActivityLogResponse(
                l.id(), l.sessionId(), l.userMessageId(), l.assistantMessageId(),
                l.userContent(), l.nluIntent(), l.nluScenarioCode(), l.nluScenarioName(),
                l.nluAgentSkill(), l.nluConfidence(), l.nluSlots(), l.nluMissingSlots(),
                l.nluReasoning(), l.llmEndpoint(), l.llmModel(),
                l.llmRequestJson(), l.llmResponseText(),
                l.status(), l.errorMessage(),
                l.nluDurationMs(), l.llmDurationMs(), l.totalDurationMs(),
                l.createdAt()
        );
    }

    private static FeedbackResponse toFeedbackResponse(MessageFeedback f) {
        return new FeedbackResponse(
                f.id(), f.sessionId(), f.messageId(), f.feedbackType(), f.feedbackNote(), f.createdAt()
        );
    }
}
