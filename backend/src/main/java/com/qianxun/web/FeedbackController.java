package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.service.QianXunServiceActivityLog;
import com.qianxun.service.QianXunServiceChatSession;
import com.qianxun.web.dto.FeedbackRequest;
import com.qianxun.web.dto.FeedbackResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消息反馈接口（点赞 / 点踩）
 * POST   /QianXunService/sessions/{sessionId}/messages/{messageId}/feedback
 * GET    /QianXunService/sessions/{sessionId}/messages/{messageId}/feedback
 * DELETE /QianXunService/sessions/{sessionId}/messages/{messageId}/feedback
 */
@RestController
@RequestMapping("/QianXunService/sessions/{sessionId}/messages/{messageId}/feedback")
public class FeedbackController {

    private final QianXunServiceActivityLog activityLogService;
    private final QianXunServiceChatSession chatSessionService;

    public FeedbackController(QianXunServiceActivityLog activityLogService, QianXunServiceChatSession chatSessionService) {
        this.activityLogService = activityLogService;
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public FeedbackResponse submit(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("messageId") String messageId,
            @RequestBody FeedbackRequest request
    ) {
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(sessionId, userId);
        return activityLogService.submitFeedback(
                userId, sessionId, messageId,
                request.feedbackType(), request.feedbackNote()
        );
    }

    @GetMapping
    public ResponseEntity<FeedbackResponse> get(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("messageId") String messageId
    ) {
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(sessionId, userId);
        return activityLogService.getFeedback(messageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("messageId") String messageId
    ) {
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(sessionId, userId);
        activityLogService.deleteFeedback(messageId);
        return ResponseEntity.ok(Map.of("message", "feedback removed"));
    }
}
