package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.service.QianXunServiceChatSession;
import com.qianxun.service.QianXunServiceChatStream;
import com.qianxun.service.stream.ActiveRunRegistry;
import com.qianxun.service.stream.ChatRun;
import com.qianxun.web.dto.ActiveRunResponse;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.StreamChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

@RestController
@RequestMapping("/QianXunService/sessions/{sessionId}/chat")
@Tag(name = "流式对话", description = "SSE 流式聊天、Run 控制与断线重连")
public class ChatStreamController {

    private final QianXunServiceChatStream chatStreamService;
    private final QianXunServiceChatSession chatSessionService;
    private final ActiveRunRegistry activeRunRegistry;
    private final Executor sseExecutor;

    public ChatStreamController(
            QianXunServiceChatStream chatStreamService,
            QianXunServiceChatSession chatSessionService,
            ActiveRunRegistry activeRunRegistry,
            @Qualifier("sseExecutor") Executor sseExecutor
    ) {
        this.chatStreamService = chatStreamService;
        this.chatSessionService = chatSessionService;
        this.activeRunRegistry = activeRunRegistry;
        this.sseExecutor = sseExecutor;
    }

    /**
     * 同时声明 JSON：冲突时直接返回 409 JSON。仅声明 SSE 时，内容协商常让异常处理变成空正文，
     * 前端只能看到「HTTP 409」。
     */
    @PostMapping(
            value = "/stream",
            produces = { MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE }
    )
    public Object stream(
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody StreamChatRequest request
    ) {
        String userId = UserContext.getCurrentUserId();
        chatSessionService.ensureSessionOwnership(sessionId, userId);

        var begun = activeRunRegistry.tryBegin(sessionId, userId);
        if (begun.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(HttpStatus.CONFLICT.value(),
                            "该会话正在输出中，请等待完成或先停止后再发送"));
        }
        ChatRun run = begun.get();

        SseEmitter emitter = new SseEmitter(0L);
        run.addSubscriber(emitter, 0);

        String modelCode = request.modelCode();
        String agentCode = request.agentCode();
        String hermesProfile = request.hermesProfile();
        java.util.List<String> fileIds = request.fileIds() == null
                ? java.util.List.of()
                : java.util.List.copyOf(request.fileIds());
        String skillName = request.skillName();
        sseExecutor.execute(() -> chatStreamService.streamAnswer(
                userId, sessionId, request.content(), modelCode, agentCode, hermesProfile, fileIds, skillName,
                request.goal(), request.clearGoal(), request.agentsStatus(), request.slashCommand(), run));
        return emitter;
    }

    @GetMapping(value = "/stream/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "afterSeq", defaultValue = "0") long afterSeq
    ) {
        String userId = UserContext.getCurrentUserId();
        return chatStreamService.subscribe(userId, sessionId, afterSeq);
    }

    @GetMapping("/runs/active")
    public ResponseEntity<ApiResponse<ActiveRunResponse>> activeRun(
            @PathVariable("sessionId") String sessionId
    ) {
        String userId = UserContext.getCurrentUserId();
        return chatStreamService.activeRun(userId, sessionId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/stream/stop")
    public ApiResponse<Void> stop(@PathVariable("sessionId") String sessionId) {
        String userId = UserContext.getCurrentUserId();
        chatStreamService.stopRun(userId, sessionId);
        return ApiResponse.success(null);
    }
}
