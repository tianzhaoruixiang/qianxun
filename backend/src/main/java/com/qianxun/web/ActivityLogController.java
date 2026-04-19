package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.service.QianXunActivityLogService;
import com.qianxun.service.QianXunChatSessionService;
import com.qianxun.web.dto.ActivityLogResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 活动日志查询接口（供开发/运营自检优化使用）
 * GET /api/sessions/{sessionId}/activity-logs
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/activity-logs")
public class ActivityLogController {

    private final QianXunActivityLogService activityLogService;
    private final QianXunChatSessionService chatSessionService;

    public ActivityLogController(QianXunActivityLogService activityLogService, QianXunChatSessionService chatSessionService) {
        this.activityLogService = activityLogService;
        this.chatSessionService = chatSessionService;
    }

    @GetMapping
    public List<ActivityLogResponse> list(@PathVariable("sessionId") String sessionId) {
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(sessionId, userId);
        return activityLogService.listBySession(sessionId);
    }
}
