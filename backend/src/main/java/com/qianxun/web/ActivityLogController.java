package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.service.QianXunServiceActivityLog;
import com.qianxun.service.QianXunServiceChatSession;
import com.qianxun.web.dto.ActivityLogResponse;
import com.qianxun.web.dto.ActivityLogListRequest;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 活动日志查询接口（供开发/运营自检优化使用）
 * POST /QianXunService/activity-logs/list
 */
@RestController
@RequestMapping("/QianXunService/activity-logs")
public class ActivityLogController {

    private final QianXunServiceActivityLog activityLogService;
    private final QianXunServiceChatSession chatSessionService;

    public ActivityLogController(QianXunServiceActivityLog activityLogService, QianXunServiceChatSession chatSessionService) {
        this.activityLogService = activityLogService;
        this.chatSessionService = chatSessionService;
    }

    @PostMapping("/list")
    public ApiResponse<List<ActivityLogResponse>> list(@RequestBody ApiRequest<ActivityLogListRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        String sessionId = ApiRequestSupport.jsonArg(request).sessionId();
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(sessionId, userId);
        return ApiResponse.success(activityLogService.listBySession(sessionId));
    }
}
