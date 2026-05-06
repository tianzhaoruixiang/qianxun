package com.qianxun.web;

import com.qianxun.service.QianXunServiceChatSession;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.ChatMessageResponse;
import com.qianxun.web.dto.ChatSessionResponse;
import com.qianxun.web.dto.CreateSessionRequest;
import com.qianxun.web.dto.IdRequest;
import com.qianxun.web.dto.SessionMessageListRequest;
import com.qianxun.web.dto.UpdateSessionApiRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/QianXunService/sessions")
public class SessionController {

    private final QianXunServiceChatSession chatSessionService;

    public SessionController(QianXunServiceChatSession chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping("/create")
    public ApiResponse<ChatSessionResponse> create(@RequestBody(required = false) ApiRequest<CreateSessionRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(chatSessionService.create(ApiRequestSupport.jsonArg(request)));
    }

    @PostMapping("/list")
    public ApiResponse<List<ChatSessionResponse>> list(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(chatSessionService.list());
    }

    @PostMapping("/get")
    public ApiResponse<ChatSessionResponse> get(@RequestBody ApiRequest<IdRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(chatSessionService.get(ApiRequestSupport.jsonArg(request).id()));
    }

    @PostMapping("/update")
    public ApiResponse<ChatSessionResponse> update(@RequestBody ApiRequest<UpdateSessionApiRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpdateSessionApiRequest arg = ApiRequestSupport.jsonArg(request);
        return ApiResponse.success(chatSessionService.update(arg.id(), new com.qianxun.web.dto.UpdateSessionRequest(arg.title())));
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody ApiRequest<IdRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        chatSessionService.delete(ApiRequestSupport.jsonArg(request).id());
        return ApiResponse.success(null);
    }

    @PostMapping("/messages")
    public ApiResponse<List<ChatMessageResponse>> messages(@RequestBody ApiRequest<SessionMessageListRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(chatSessionService.listMessages(ApiRequestSupport.jsonArg(request).sessionId()));
    }
}
