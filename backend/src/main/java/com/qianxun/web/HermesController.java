package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.service.ContextWindowResolver;
import com.qianxun.service.HermesLiveTranscriptService;
import com.qianxun.service.HermesSkillService;
import com.qianxun.service.HermesToolsetService;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.CreateHermesProfileRequest;
import com.qianxun.web.dto.HermesLiveDelegationResponse;
import com.qianxun.web.dto.HermesLiveTaskLogResponse;
import com.qianxun.web.dto.HermesLiveTranscriptContentResponse;
import com.qianxun.web.dto.HermesLiveTranscriptListRequest;
import com.qianxun.web.dto.HermesLiveTranscriptReadRequest;
import com.qianxun.web.dto.HermesProfileResponse;
import com.qianxun.web.dto.HermesSkillFileNodeResponse;
import com.qianxun.web.dto.HermesSkillFileRequest;
import com.qianxun.web.dto.HermesSkillFileResponse;
import com.qianxun.web.dto.HermesSkillItemResponse;
import com.qianxun.web.dto.HermesSkillListRequest;
import com.qianxun.web.dto.HermesSkillTreeRequest;
import com.qianxun.web.dto.HermesSkillUploadResponse;
import com.qianxun.web.dto.HermesSoulRequest;
import com.qianxun.web.dto.HermesSoulResponse;
import com.qianxun.web.dto.HermesToolItemResponse;
import com.qianxun.web.dto.HermesToolsetItemResponse;
import com.qianxun.web.dto.HermesToolsetListRequest;
import com.qianxun.web.dto.HermesToolsetToggleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Tag(name = "Hermes 智能体", description = "Profile、Soul、技能、工具集与委派观测")
@RestController
@RequestMapping("/QianXunService/hermes")
public class HermesController {

    private final HermesAgentClient hermesAgentClient;
    private final HermesSkillService hermesSkillService;
    private final HermesToolsetService hermesToolsetService;
    private final HermesLiveTranscriptService hermesLiveTranscriptService;
    private final ContextWindowResolver contextWindowResolver;

    public HermesController(
            HermesAgentClient hermesAgentClient,
            HermesSkillService hermesSkillService,
            HermesToolsetService hermesToolsetService,
            HermesLiveTranscriptService hermesLiveTranscriptService,
            ContextWindowResolver contextWindowResolver
    ) {
        this.hermesAgentClient = hermesAgentClient;
        this.hermesSkillService = hermesSkillService;
        this.hermesToolsetService = hermesToolsetService;
        this.hermesLiveTranscriptService = hermesLiveTranscriptService;
        this.contextWindowResolver = contextWindowResolver;
    }

    @Operation(summary = "列出 Claude Profiles")
    @PostMapping("/profiles/list")
    public ApiResponse<List<HermesProfileResponse>> listProfiles(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        if (!hermesAgentClient.isConfigured()) {
            return ApiResponse.success(List.of());
        }
        List<HermesProfileResponse> data = hermesAgentClient.listProfiles(UserContext.getCurrentUserId()).stream()
                .map(p -> new HermesProfileResponse(
                        p.name(),
                        p.description(),
                        p.model(),
                        p.active(),
                        p.path(),
                        contextWindowResolver.enrichHermesProfileWindow(p.contextWindow(), p.model())
                ))
                .toList();
        return ApiResponse.success(data);
    }

    @Operation(summary = "创建 Claude Profile")
    @PostMapping("/profiles/create")
    public ApiResponse<Map<String, Object>> createProfile(@RequestBody ApiRequest<CreateHermesProfileRequest> request) {
        requireAgentAdmin();
        ApiRequestSupport.applyGeneralArgument(request);
        CreateHermesProfileRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        HermesAgentClient.CreateProfileResult r = hermesAgentClient.createProfile(
                UserContext.getCurrentUserId(), body.name(), body.description());
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(Map.of(
                "ok", true,
                "name", r.name(),
                "path", r.path() == null ? "" : r.path(),
                "alreadyExists", r.alreadyExists(),
                "message", r.message()
        ));
    }

    @Operation(summary = "读取 Profile Soul（CLAUDE.md）")
    @PostMapping("/profiles/soul")
    public ApiResponse<HermesSoulResponse> getSoul(@RequestBody ApiRequest<HermesSoulRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesSoulRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        if (!hermesAgentClient.isConfigured()) {
            return ApiResponse.success(new HermesSoulResponse(body.name().trim(), "", false));
        }
        HermesAgentClient.SoulResult r = hermesAgentClient.getSoul(UserContext.getCurrentUserId(), body.name());
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(new HermesSoulResponse(body.name().trim(), r.content(), r.exists()));
    }

    @Operation(summary = "更新 Profile Soul（CLAUDE.md）")
    @PostMapping("/profiles/soul/update")
    public ApiResponse<HermesSoulResponse> updateSoul(@RequestBody ApiRequest<HermesSoulRequest> request) {
        requireAgentAdmin();
        ApiRequestSupport.applyGeneralArgument(request);
        HermesSoulRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        HermesAgentClient.SoulResult r = hermesAgentClient.putSoul(
                UserContext.getCurrentUserId(), body.name(), body.content() == null ? "" : body.content());
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(new HermesSoulResponse(body.name().trim(), r.content(), r.exists()));
    }

    @Operation(summary = "代理 Claude 网关状态")
    @GetMapping("/gateway/status")
    public ApiResponse<Map<String, Object>> gatewayStatus() {
        if (!hermesAgentClient.isConfigured()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", false);
            data.put("configured", false);
            data.put("runner", "claude-code");
            data.put("authRequired", true);
            return ApiResponse.success(data);
        }
        Optional<HermesAgentClient.GatewayStatus> status = hermesAgentClient.getGatewayStatus();
        if (status.isEmpty()) {
            return ApiResponse.error(502, "无法读取 Claude 网关状态");
        }
        HermesAgentClient.GatewayStatus s = status.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", s.ok());
        data.put("runner", s.runner());
        data.put("configured", s.configured());
        data.put("model", s.model());
        data.put("authRequired", s.authRequired());
        return ApiResponse.success(data);
    }

    @Operation(summary = "列出技能")
    @PostMapping("/skills/list")
    public ApiResponse<List<HermesSkillItemResponse>> listSkills(@RequestBody(required = false) ApiRequest<HermesSkillListRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesSkillListRequest body = ApiRequestSupport.jsonArg(request);
        String profile = body == null ? "" : body.profile();
        if (!hermesAgentClient.isConfigured()) {
            return ApiResponse.success(List.of());
        }
        List<HermesSkillItemResponse> data = hermesSkillService.list(profile).stream()
                .map(s -> new HermesSkillItemResponse(s.name(), s.description(), s.category(), s.enabled(), s.provenance()))
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/skills/tree")
    public ApiResponse<List<HermesSkillFileNodeResponse>> skillTree(@RequestBody ApiRequest<HermesSkillTreeRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesSkillTreeRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        try {
            List<HermesSkillFileNodeResponse> data = hermesSkillService.tree(body.profile(), body.name()).stream()
                    .map(n -> new HermesSkillFileNodeResponse(n.path(), n.name(), n.directory(), n.size(), n.text()))
                    .toList();
            return ApiResponse.success(data);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ApiResponse.error(502, ex.getMessage());
        }
    }

    @PostMapping("/skills/file")
    public ApiResponse<HermesSkillFileResponse> readSkillFile(@RequestBody ApiRequest<HermesSkillFileRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesSkillFileRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        String path = body.path() == null || body.path().isBlank() ? "SKILL.md" : body.path();
        try {
            HermesSkillService.FileBody file = hermesSkillService.readFile(body.profile(), body.name(), path);
            if (!file.ok()) {
                return ApiResponse.error(502, file.message());
            }
            return ApiResponse.success(new HermesSkillFileResponse(file.path(), file.content(), file.text(), body.name().trim()));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ApiResponse.error(502, ex.getMessage());
        }
    }

    @PostMapping("/skills/file/update")
    public ApiResponse<HermesSkillFileResponse> updateSkillFile(@RequestBody ApiRequest<HermesSkillFileRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesSkillFileRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        String path = body.path() == null || body.path().isBlank() ? "SKILL.md" : body.path();
        try {
            HermesAgentClient.ManagedWriteResult r = hermesSkillService.saveFile(
                    body.profile(), body.name(), path, body.content() == null ? "" : body.content());
            if (!r.ok()) {
                return ApiResponse.error(502, r.message());
            }
            return ApiResponse.success(new HermesSkillFileResponse(path, body.content() == null ? "" : body.content(), true, body.name().trim()));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ApiResponse.error(502, ex.getMessage());
        }
    }

    @PostMapping("/skills/toggle")
    public ApiResponse<Map<String, Object>> toggleSkill(@RequestBody ApiRequest<HermesSkillFileRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesSkillFileRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        boolean enabled = body.enabled() == null || body.enabled();
        HermesAgentClient.SkillWriteResult r = hermesSkillService.toggle(body.profile(), body.name(), enabled);
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(Map.of("ok", true, "name", r.name(), "enabled", enabled));
    }

    @PostMapping(value = "/skills/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<HermesSkillUploadResponse> uploadSkillZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "profile", required = false) String profile
    ) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "请上传 zip 技能包");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return ApiResponse.error(400, "仅支持 .zip 技能包");
        }
        try {
            HermesSkillService.UploadResult r = hermesSkillService.uploadZip(profile, filename, file.getBytes());
            if (!r.ok()) {
                String msg = r.errors().isEmpty() ? "上传失败" : String.join("; ", r.errors());
                return ApiResponse.error(502, msg);
            }
            return ApiResponse.success(new HermesSkillUploadResponse(true, r.installed(), r.errors()));
        } catch (Exception ex) {
            return ApiResponse.error(502, "上传失败: " + ex.getMessage());
        }
    }

    @GetMapping("/skills/download")
    public ResponseEntity<byte[]> downloadSkillZip(
            @RequestParam("name") String name,
            @RequestParam(value = "profile", required = false) String profile
    ) {
        HermesSkillService.ZipDownload zip = hermesSkillService.downloadZip(profile, name);
        if (!zip.ok()) {
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"code\":502,\"message\":\"" + escapeJson(zip.message()) + "\"}").getBytes(StandardCharsets.UTF_8));
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(zip.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip.bytes());
    }

    @PostMapping("/tools/list")
    public ApiResponse<List<HermesToolsetItemResponse>> listToolsets(
            @RequestBody(required = false) ApiRequest<HermesToolsetListRequest> request
    ) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesToolsetListRequest body = ApiRequestSupport.jsonArg(request);
        String profile = body == null ? "" : body.profile();
        if (!hermesAgentClient.isConfigured()) {
            return ApiResponse.success(List.of());
        }
        List<HermesToolsetItemResponse> data = hermesToolsetService.list(profile).stream()
                .map(HermesController::toToolsetResponse)
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/tools/toggle")
    public ApiResponse<Map<String, Object>> toggleToolset(@RequestBody ApiRequest<HermesToolsetToggleRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesToolsetToggleRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ApiResponse.error(400, "name 不能为空");
        }
        boolean enabled = body.enabled() == null || body.enabled();
        HermesAgentClient.ToolsetWriteResult r = hermesToolsetService.toggle(body.profile(), body.name(), enabled);
        if (!r.ok()) {
            return ApiResponse.error(502, r.message());
        }
        return ApiResponse.success(Map.of("ok", true, "name", r.name(), "enabled", r.enabled()));
    }

    /** 列出当前 profile 下最近的子智能体 live transcript（delegate_task 落盘日志）。 */
    @PostMapping("/delegation/live/list")
    public ApiResponse<List<HermesLiveDelegationResponse>> listLiveTranscripts(
            @RequestBody(required = false) ApiRequest<HermesLiveTranscriptListRequest> request
    ) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesLiveTranscriptListRequest body = ApiRequestSupport.jsonArg(request);
        String profile = body == null ? "" : body.profile();
        int limit = body == null || body.limit() == null ? 0 : body.limit();
        if (!hermesAgentClient.isConfigured()) {
            return ApiResponse.success(List.of());
        }
        List<HermesLiveDelegationResponse> data = hermesLiveTranscriptService.listRecent(profile, limit).stream()
                .map(HermesController::toDelegationResponse)
                .toList();
        return ApiResponse.success(data);
    }

    /** 读取某一委派的 live transcript（可指定 taskIndex；不传则拼接全部子任务）。 */
    @PostMapping("/delegation/live/read")
    public ApiResponse<HermesLiveTranscriptContentResponse> readLiveTranscript(
            @RequestBody ApiRequest<HermesLiveTranscriptReadRequest> request
    ) {
        ApiRequestSupport.applyGeneralArgument(request);
        HermesLiveTranscriptReadRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.delegationId() == null || body.delegationId().isBlank()) {
            return ApiResponse.error(400, "delegationId 不能为空");
        }
        if (!hermesAgentClient.isConfigured()) {
            return ApiResponse.error(503, "未启用 Hermes");
        }
        HermesLiveTranscriptService.LogContent r = hermesLiveTranscriptService.readTaskLog(
                body.profile(),
                body.delegationId(),
                body.taskIndex(),
                body.maxChars() == null ? 0 : body.maxChars()
        );
        if (!r.ok()) {
            return ApiResponse.error(404, r.message());
        }
        return ApiResponse.success(new HermesLiveTranscriptContentResponse(
                true, r.delegationId(), r.taskIndex(), r.path(), r.content(), r.message()
        ));
    }

    private static HermesLiveDelegationResponse toDelegationResponse(HermesLiveTranscriptService.DelegationInfo d) {
        List<HermesLiveTaskLogResponse> tasks = d.tasks().stream()
                .map(t -> new HermesLiveTaskLogResponse(t.index(), t.path(), t.goal(), t.status(), t.size()))
                .toList();
        return new HermesLiveDelegationResponse(
                d.delegationId(), d.path(), d.started(), d.completed(), d.taskCount(), tasks
        );
    }

    private static HermesToolsetItemResponse toToolsetResponse(HermesToolsetService.ToolsetView t) {
        List<HermesToolItemResponse> tools = t.tools().stream()
                .map(x -> new HermesToolItemResponse(x.name(), x.displayName(), x.iconKind(), x.enabled()))
                .toList();
        return new HermesToolsetItemResponse(
                t.name(), t.label(), t.description(), t.platform(), t.platformLabel(),
                t.enabled(), t.configured(), tools
        );
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private static void requireAgentAdmin() {
        if (!UserContext.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可新建、修改或删除智能体");
        }
    }
}
