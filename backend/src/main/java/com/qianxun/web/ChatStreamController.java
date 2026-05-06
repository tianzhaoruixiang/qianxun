package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.service.QianXunServiceChatSession;
import com.qianxun.service.QianXunServiceChatStream;
import com.qianxun.web.dto.StreamChatRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/QianXunService/sessions/{sessionId}/chat")
public class ChatStreamController {

    private final QianXunServiceChatStream chatStreamService;
    private final QianXunServiceChatSession chatSessionService;
    private final Executor sseExecutor;

    public ChatStreamController(
            QianXunServiceChatStream chatStreamService,
            QianXunServiceChatSession chatSessionService,
            @Qualifier("sseExecutor") Executor sseExecutor
    ) {
        this.chatStreamService = chatStreamService;
        this.chatSessionService = chatSessionService;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody StreamChatRequest request
    ) {
        // 在请求线程（ThreadLocal 有效）中捕获 userId，并提前验证会话归属
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(sessionId, userId);

        SseEmitter emitter = new SseEmitter(0L);
        // 将 userId 和 deepMode 显式传入 SSE 工作线程，因为 ThreadLocal 不跨线程
        boolean deepMode = request.isDeep();
        String confirmedScenarioCode = request.confirmedScenarioCode();
        String modelCode = request.modelCode();
        List<String> datasetCodes = request.resolvedDatasetCodes();
        var selectedFileIds = request.selectedFileIds();
        sseExecutor.execute(() -> chatStreamService.streamAnswer(
                userId, sessionId, request.content(), deepMode, confirmedScenarioCode, modelCode, datasetCodes, selectedFileIds, emitter));
        return emitter;
    }
}
