package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.service.RunMetricsService;
import com.qianxun.service.stream.ActiveRunRegistry;
import com.qianxun.service.stream.ChatRun;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.ListRunsRequest;
import com.qianxun.web.dto.RunMetricsResponse;
import com.qianxun.web.dto.RunSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "运行观测", description = "跨会话 Run 列表、指标与 trace 关联")
@RestController
@RequestMapping("/QianXunService/runs")
public class RunController {

    private final ActiveRunRegistry activeRunRegistry;
    private final RunMetricsService runMetricsService;

    public RunController(ActiveRunRegistry activeRunRegistry, RunMetricsService runMetricsService) {
        this.activeRunRegistry = activeRunRegistry;
        this.runMetricsService = runMetricsService;
    }

    @Operation(summary = "列出当前用户的 Run（含宽限期内已结束）")
    @PostMapping("/list")
    public ApiResponse<List<RunSummaryResponse>> list(@RequestBody(required = false) ApiRequest<ListRunsRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        ListRunsRequest body = ApiRequestSupport.jsonArg(request);
        boolean runningOnly = body != null && Boolean.TRUE.equals(body.runningOnly());
        int limit = body == null || body.limit() == null ? 50 : Math.min(Math.max(body.limit(), 1), 200);
        List<ChatRun> runs = activeRunRegistry.listByUser(UserContext.getCurrentUserId());
        if (runningOnly) {
            runs = runs.stream().filter(ChatRun::isRunning).toList();
        }
        List<RunSummaryResponse> data = runs.stream()
                .limit(limit)
                .map(RunSummaryResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @Operation(summary = "运行指标快照（JSON；Prometheus 见 /prometheus）")
    @GetMapping("/metrics")
    public ApiResponse<RunMetricsResponse> metrics() {
        List<ChatRun> mine = activeRunRegistry.listByUser(UserContext.getCurrentUserId());
        List<RunSummaryResponse> running = activeRunRegistry.listRunning().stream()
                .filter(r -> UserContext.getCurrentUserId().equals(r.userId()))
                .map(RunSummaryResponse::from)
                .toList();
        return ApiResponse.success(new RunMetricsResponse(
                running.size(),
                mine.size(),
                runMetricsService.uptimeMs(),
                runMetricsService.statusCounts(mine),
                running
        ));
    }
}
