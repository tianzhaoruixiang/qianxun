package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.service.AgentTaskService;
import com.qianxun.web.dto.AgentTaskIdRequest;
import com.qianxun.web.dto.AgentTaskResponse;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.ListAgentTasksRequest;
import com.qianxun.web.dto.SubmitAgentTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "智能体任务", description = "内部编排：A2A 形 Task（id / state / cancel），子 ChatRun 投影到干警会话")
@RestController
@RequestMapping("/QianXunService/agent-tasks")
public class AgentTaskController {

    private final AgentTaskService agentTaskService;

    public AgentTaskController(AgentTaskService agentTaskService) {
        this.agentTaskService = agentTaskService;
    }

    @Operation(summary = "提交任务（启动子 ChatRun）")
    @PostMapping("/submit")
    public ApiResponse<AgentTaskResponse> submit(@RequestBody SubmitAgentTaskRequest request) {
        return ApiResponse.success(agentTaskService.submit(request));
    }

    @Operation(summary = "查询任务")
    @PostMapping("/get")
    public ApiResponse<AgentTaskResponse> get(@RequestBody AgentTaskIdRequest request) {
        if (request == null || request.id() == null || request.id().isBlank()) {
            return ApiResponse.error(400, "id 不能为空");
        }
        return ApiResponse.success(agentTaskService.get(request.id(), UserContext.getCurrentUserId()));
    }

    @Operation(summary = "取消任务")
    @PostMapping("/cancel")
    public ApiResponse<AgentTaskResponse> cancel(@RequestBody AgentTaskIdRequest request) {
        if (request == null || request.id() == null || request.id().isBlank()) {
            return ApiResponse.error(400, "id 不能为空");
        }
        return ApiResponse.success(agentTaskService.cancel(request.id(), UserContext.getCurrentUserId()));
    }

    @Operation(summary = "列出父轮次下的任务")
    @PostMapping("/list")
    public ApiResponse<List<AgentTaskResponse>> list(@RequestBody(required = false) ListAgentTasksRequest request) {
        String runId = request == null ? "" : request.parentRunId();
        String sessionId = request == null ? "" : request.parentSessionId();
        return ApiResponse.success(agentTaskService.list(runId, sessionId, UserContext.getCurrentUserId()));
    }
}
