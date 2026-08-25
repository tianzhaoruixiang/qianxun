package com.qianxun.web;

import com.qianxun.service.HermesLiveTranscriptService;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.DelegationDeleteRequest;
import com.qianxun.web.dto.DelegationGetRequest;
import com.qianxun.web.dto.HermesLiveDelegationResponse;
import com.qianxun.web.dto.HermesLiveTaskLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "多智能体委派", description = "委派 CRUD 与 live transcript 管理")
@RestController
@RequestMapping("/QianXunService/hermes/delegation")
public class DelegationController {

    private final HermesLiveTranscriptService transcriptService;

    public DelegationController(HermesLiveTranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    @Operation(summary = "获取单个委派详情")
    @PostMapping("/get")
    public ApiResponse<HermesLiveDelegationResponse> get(@RequestBody ApiRequest<DelegationGetRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        DelegationGetRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.delegationId() == null || body.delegationId().isBlank()) {
            return ApiResponse.error(400, "delegationId 不能为空");
        }
        HermesLiveTranscriptService.DelegationInfo info = transcriptService.loadDelegation(body.profile(), body.delegationId());
        if (info == null) {
            return ApiResponse.error(404, "委派不存在");
        }
        return ApiResponse.success(toResponse(info));
    }

    @Operation(summary = "删除委派目录及日志")
    @PostMapping("/delete")
    public ApiResponse<Map<String, Object>> delete(@RequestBody ApiRequest<DelegationDeleteRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        DelegationDeleteRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.delegationId() == null || body.delegationId().isBlank()) {
            return ApiResponse.error(400, "delegationId 不能为空");
        }
        HermesLiveTranscriptService.DeleteResult r = transcriptService.deleteDelegation(body.profile(), body.delegationId());
        if (!r.ok()) {
            return ApiResponse.error(r.notFound() ? 404 : 502, r.message());
        }
        return ApiResponse.success(Map.of("ok", true, "delegationId", r.delegationId(), "path", r.path()));
    }

    @Operation(summary = "标记委派为已取消（写入 manifest）")
    @PostMapping("/cancel")
    public ApiResponse<Map<String, Object>> cancel(@RequestBody ApiRequest<DelegationDeleteRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        DelegationDeleteRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.delegationId() == null || body.delegationId().isBlank()) {
            return ApiResponse.error(400, "delegationId 不能为空");
        }
        HermesLiveTranscriptService.CancelResult r = transcriptService.cancelDelegation(body.profile(), body.delegationId());
        if (!r.ok()) {
            return ApiResponse.error(r.notFound() ? 404 : 502, r.message());
        }
        return ApiResponse.success(Map.of("ok", true, "delegationId", r.delegationId(), "status", "cancelled"));
    }

    private static HermesLiveDelegationResponse toResponse(HermesLiveTranscriptService.DelegationInfo d) {
        List<HermesLiveTaskLogResponse> tasks = d.tasks().stream()
                .map(t -> new HermesLiveTaskLogResponse(t.index(), t.path(), t.goal(), t.status(), t.size()))
                .toList();
        return new HermesLiveDelegationResponse(
                d.delegationId(), d.path(), d.started(), d.completed(), d.taskCount(), tasks
        );
    }
}
