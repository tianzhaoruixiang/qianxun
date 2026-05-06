package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.service.QianXunServiceActivityLog;
import com.qianxun.service.QianXunServiceChatSession;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.FeedbackApiRequest;
import com.qianxun.web.dto.FeedbackResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息反馈接口（点赞 / 点踩）
 */
@RestController
@RequestMapping("/QianXunService/feedback")
public class FeedbackController {

    private final QianXunServiceActivityLog activityLogService;
    private final QianXunServiceChatSession chatSessionService;

    public FeedbackController(QianXunServiceActivityLog activityLogService, QianXunServiceChatSession chatSessionService) {
        this.activityLogService = activityLogService;
        this.chatSessionService = chatSessionService;
    }

    @PostMapping("/submit")
    public ApiResponse<FeedbackResponse> submit(@RequestBody ApiRequest<FeedbackApiRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        FeedbackApiRequest arg = ApiRequestSupport.jsonArg(request);
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(arg.sessionId(), userId);
        return ApiResponse.success(activityLogService.submitFeedback(
                userId, arg.sessionId(), arg.messageId(),
                arg.feedbackType(), arg.feedbackNote()
        ));
    }

    @PostMapping("/get")
    public ApiResponse<FeedbackResponse> get(@RequestBody ApiRequest<FeedbackApiRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        FeedbackApiRequest arg = ApiRequestSupport.jsonArg(request);
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(arg.sessionId(), userId);
        return ApiResponse.success(activityLogService.getFeedback(arg.messageId()).orElse(null));
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody ApiRequest<FeedbackApiRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        FeedbackApiRequest arg = ApiRequestSupport.jsonArg(request);
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(arg.sessionId(), userId);
        activityLogService.deleteFeedback(arg.messageId());
        return ApiResponse.success(null);
    }
}
