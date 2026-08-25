package com.qianxun.web;

import com.qianxun.service.McpPluginService;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.McpServerResponse;
import com.qianxun.web.dto.PluginItemResponse;
import com.qianxun.web.dto.UpsertMcpServerRequest;
import com.qianxun.web.dto.UpsertPluginRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "MCP 与插件", description = "MCP Server 与插件 manifest 管理")
@RestController
@RequestMapping("/QianXunService/hermes")
public class McpController {

    private final McpPluginService mcpPluginService;

    public McpController(McpPluginService mcpPluginService) {
        this.mcpPluginService = mcpPluginService;
    }

    @Operation(summary = "列出 MCP Server")
    @PostMapping("/mcp/list")
    public ApiResponse<List<McpServerResponse>> listMcp(@RequestBody(required = false) ApiRequest<Map<String, String>> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        String profile = profileOf(request);
        List<McpServerResponse> data = mcpPluginService.listMcp(profile).stream()
                .map(McpController::toMcpResponse)
                .toList();
        return ApiResponse.success(data);
    }

    @Operation(summary = "创建或更新 MCP Server")
    @PostMapping("/mcp/upsert")
    public ApiResponse<McpServerResponse> upsertMcp(@RequestBody ApiRequest<UpsertMcpServerRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertMcpServerRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        var r = mcpPluginService.upsertMcp(body.profile(), body.name(), body.command(),
                body.args(), body.env(), body.enabled() == null || body.enabled(),
                body.description(), body.transport(), body.url());
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(new McpServerResponse(
                r.name(), body.command(), body.args(), body.env(),
                body.enabled() == null || body.enabled(),
                body.description(), body.transport(), body.url()
        ));
    }

    @Operation(summary = "启用/禁用 MCP Server")
    @PostMapping("/mcp/toggle")
    public ApiResponse<Map<String, Object>> toggleMcp(@RequestBody ApiRequest<UpsertMcpServerRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertMcpServerRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        boolean enabled = body.enabled() == null || body.enabled();
        var r = mcpPluginService.toggleMcp(body.profile(), body.name(), enabled);
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(Map.of("ok", true, "name", r.name(), "enabled", enabled));
    }

    @Operation(summary = "删除 MCP Server")
    @PostMapping("/mcp/delete")
    public ApiResponse<Void> deleteMcp(@RequestBody ApiRequest<UpsertMcpServerRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertMcpServerRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        var r = mcpPluginService.deleteMcp(body.profile(), body.name());
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(null);
    }

    @Operation(summary = "列出插件")
    @PostMapping("/plugins/list")
    public ApiResponse<List<PluginItemResponse>> listPlugins(@RequestBody(required = false) ApiRequest<Map<String, String>> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        String profile = profileOf(request);
        List<PluginItemResponse> data = mcpPluginService.listPlugins(profile).stream()
                .map(p -> new PluginItemResponse(
                        p.name(), p.path(), p.version(), p.enabled(), p.description(), p.manifest()))
                .toList();
        return ApiResponse.success(data);
    }

    @Operation(summary = "创建或更新插件")
    @PostMapping("/plugins/upsert")
    public ApiResponse<PluginItemResponse> upsertPlugin(@RequestBody ApiRequest<UpsertPluginRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertPluginRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        var r = mcpPluginService.upsertPlugin(body.profile(), body.name(), body.path(),
                body.version(), body.enabled() == null || body.enabled(),
                body.description(), body.manifest());
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(new PluginItemResponse(
                r.name(), body.path(), body.version(),
                body.enabled() == null || body.enabled(), body.description(), body.manifest()
        ));
    }

    @Operation(summary = "启用/禁用插件")
    @PostMapping("/plugins/toggle")
    public ApiResponse<Map<String, Object>> togglePlugin(@RequestBody ApiRequest<UpsertPluginRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertPluginRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        boolean enabled = body.enabled() == null || body.enabled();
        var r = mcpPluginService.togglePlugin(body.profile(), body.name(), enabled);
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(Map.of("ok", true, "name", r.name(), "enabled", enabled));
    }

    @Operation(summary = "删除插件")
    @PostMapping("/plugins/delete")
    public ApiResponse<Void> deletePlugin(@RequestBody ApiRequest<UpsertPluginRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertPluginRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        var r = mcpPluginService.deletePlugin(body.profile(), body.name());
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(null);
    }

    private static String profileOf(ApiRequest<?> request) {
        Object arg = ApiRequestSupport.jsonArg(request);
        if (arg instanceof Map<?, ?> map) {
            Object p = map.get("profile");
            if (p != null) {
                return String.valueOf(p);
            }
        }
        return "";
    }

    private static McpServerResponse toMcpResponse(com.qianxun.llm.HermesAgentClient.McpServerInfo s) {
        return new McpServerResponse(
                s.name(), s.command(), s.args(), s.env(), s.enabled(),
                s.description(), s.transport(), s.url()
        );
    }
}
