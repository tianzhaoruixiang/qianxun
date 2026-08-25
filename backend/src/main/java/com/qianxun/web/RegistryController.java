package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.DatasetRegistryItem;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.DatasetRegistryRepository;
import com.qianxun.repo.ModelRegistryRepository;
import com.qianxun.service.QianXunServiceChatSession;
import com.qianxun.web.dto.AgentRegistryResponse;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.DeleteAgentRegistryRequest;
import com.qianxun.web.dto.DatasetRegistryResponse;
import com.qianxun.web.dto.ModelRegistryResponse;
import com.qianxun.web.dto.UpsertAgentRegistryRequest;
import com.qianxun.web.dto.UpsertDatasetRegistryRequest;
import com.qianxun.web.dto.UpsertModelRegistryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "注册表", description = "智能体、模型与数据集注册表")
@RestController
@RequestMapping("/QianXunService/registry")
public class RegistryController {

    private final AgentRegistryRepository agentRepository;
    private final ModelRegistryRepository modelRepository;
    private final DatasetRegistryRepository datasetRepository;
    private final HermesAgentClient hermesAgentClient;
    private final QianXunServiceChatSession sessionService;

    public RegistryController(
            AgentRegistryRepository agentRepository,
            ModelRegistryRepository modelRepository,
            DatasetRegistryRepository datasetRepository,
            HermesAgentClient hermesAgentClient,
            QianXunServiceChatSession sessionService
    ) {
        this.agentRepository = agentRepository;
        this.modelRepository = modelRepository;
        this.datasetRepository = datasetRepository;
        this.hermesAgentClient = hermesAgentClient;
        this.sessionService = sessionService;
    }

    @Operation(summary = "列出智能体注册表")
    @PostMapping("/agents/list")
    public ApiResponse<List<AgentRegistryResponse>> listAgents(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        boolean enabledOnly = true;
        Object arg = ApiRequestSupport.jsonArg(request);
        if (arg instanceof java.util.Map<?, ?> map) {
            Object v = map.get("enabledOnly");
            if (v instanceof Boolean b) {
                enabledOnly = b;
            }
        }
        List<AgentRegistryResponse> data = agentRepository.list(enabledOnly).stream()
                .map(this::toAgentResponse)
                .toList();
        return ApiResponse.success(data);
    }

    @Operation(summary = "列出模型注册表")
    @PostMapping("/models/list")
    public ApiResponse<List<ModelRegistryResponse>> listModels(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        boolean enabledOnly = true;
        Object arg = ApiRequestSupport.jsonArg(request);
        if (arg instanceof java.util.Map<?, ?> map) {
            Object v = map.get("enabledOnly");
            if (v instanceof Boolean b) {
                enabledOnly = b;
            }
        }
        List<ModelRegistryResponse> data = modelRepository.list(enabledOnly).stream()
                .map(this::toModelResponse)
                .toList();
        return ApiResponse.success(data);
    }

    @Operation(summary = "列出数据集注册表")
    @PostMapping("/datasets/list")
    public ApiResponse<List<DatasetRegistryResponse>> listDatasets(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        boolean enabledOnly = true;
        Object arg = ApiRequestSupport.jsonArg(request);
        if (arg instanceof java.util.Map<?, ?> map) {
            Object v = map.get("enabledOnly");
            if (v instanceof Boolean b) {
                enabledOnly = b;
            }
        }
        List<DatasetRegistryResponse> data = datasetRepository.list(enabledOnly).stream()
                .map(this::toDatasetResponse)
                .toList();
        return ApiResponse.success(data);
    }

    @Operation(summary = "创建或更新智能体（同步 Profile + Soul）")
    @PostMapping("/agents/upsert")
    public ApiResponse<AgentRegistryResponse> upsertAgent(@RequestBody ApiRequest<UpsertAgentRegistryRequest> request) {
        requireAgentAdmin();
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertAgentRegistryRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || isBlank(body.code()) || isBlank(body.name())) {
            return ApiResponse.error(400, "code/name 不能为空");
        }
        if (isBlank(body.soulMd())) {
            return ApiResponse.error(400, "soulMd 不能为空");
        }
        String code = body.code().trim();
        Instant now = Instant.now();
        Optional<AgentRegistryItem> existingOpt = agentRepository.findByCode(code);
        Instant createdAt = existingOpt.map(AgentRegistryItem::createdAt).orElse(now);
        String id = existingOpt.map(AgentRegistryItem::id).orElseGet(RegistryController::newId);

        String welcomeTitle = mergeNullableText(body.welcomeTitle(), existingOpt.map(AgentRegistryItem::welcomeTitle));
        String welcomeIntro = mergeNullableText(body.welcomeIntro(), existingOpt.map(AgentRegistryItem::welcomeIntro));
        String preset1 = mergeNullableText(body.presetChat1(), existingOpt.map(AgentRegistryItem::presetChat1));
        String preset2 = mergeNullableText(body.presetChat2(), existingOpt.map(AgentRegistryItem::presetChat2));
        String preset3 = mergeNullableText(body.presetChat3(), existingOpt.map(AgentRegistryItem::presetChat3));
        String hermesProfile = mergeNullableText(body.hermesProfile(), existingOpt.map(AgentRegistryItem::hermesProfile));
        String want = hermesProfile.isBlank() ? code : hermesProfile;
        HermesAgentClient.CreateProfileResult created = hermesAgentClient.createProfile(
                UserContext.getCurrentUserId(),
                want,
                body.description() == null ? body.name() : body.description()
        );
        if (!created.ok()) {
            return ApiResponse.error(502, created.message());
        }
        hermesProfile = created.name();
        if (!isBlank(hermesProfile)) {
            hermesAgentClient.ensureProfileApiKey(hermesProfile);
        }
        HermesAgentClient.SoulResult soul = hermesAgentClient.putSoul(UserContext.getCurrentUserId(), hermesProfile, body.soulMd());
        if (!soul.ok()) {
            return ApiResponse.error(502, soul.message());
        }
        HermesAgentClient.PublishTemplateResult published = hermesAgentClient.publishProfileTemplate(
                UserContext.getCurrentUserId(), hermesProfile);
        if (!published.ok()) {
            return ApiResponse.error(502, published.message());
        }

        AgentRegistryItem item = new AgentRegistryItem(
                id, code, body.name().trim(),
                body.category() == null ? "general" : body.category().trim(),
                body.description() == null ? "" : body.description().trim(),
                body.icon() == null ? "" : body.icon().trim(),
                body.modelCode() == null ? "" : body.modelCode().trim(),
                welcomeTitle, welcomeIntro, preset1, preset2, preset3,
                "", "", "", hermesProfile,
                body.priority() == null ? 100 : body.priority(),
                body.enabled() == null || body.enabled(),
                createdAt, now
        );
        if (existingOpt.isPresent()) {
            agentRepository.updateByCode(item);
        } else {
            agentRepository.insert(item);
        }
        AgentRegistryItem latest = agentRepository.findByCode(code).orElse(item);
        return ApiResponse.success(toAgentResponse(latest));
    }

    @Operation(summary = "删除智能体及关联 Profile")
    @PostMapping("/agents/delete")
    public ApiResponse<Void> deleteAgent(@RequestBody ApiRequest<DeleteAgentRegistryRequest> request) {
        requireAgentAdmin();
        ApiRequestSupport.applyGeneralArgument(request);
        DeleteAgentRegistryRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || isBlank(body.code())) {
            return ApiResponse.error(400, "code 不能为空");
        }
        String code = body.code().trim();
        Optional<AgentRegistryItem> existing = agentRepository.findByCode(code);
        if (existing.isEmpty()) {
            return ApiResponse.error(404, "智能体不存在或已删除");
        }
        String hermesProfile = existing.get().hermesProfile();
        if (!isBlank(hermesProfile)) {
            HermesAgentClient.DeleteProfileResult deleted = hermesAgentClient.deleteProfile(
                    UserContext.getCurrentUserId(), hermesProfile);
            if (!deleted.ok()) {
                return ApiResponse.error(502, deleted.message());
            }
        }
        sessionService.deleteByAgent(code, hermesProfile);
        int n = agentRepository.deleteByCode(code);
        if (n == 0) {
            return ApiResponse.error(404, "智能体不存在或已删除");
        }
        return ApiResponse.success(null);
    }

    @Operation(summary = "创建或更新模型")
    @PostMapping("/models/upsert")
    public ApiResponse<ModelRegistryResponse> upsertModel(@RequestBody ApiRequest<UpsertModelRegistryRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertModelRegistryRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || isBlank(body.code()) || isBlank(body.name())) {
            return ApiResponse.error(400, "code/name 不能为空");
        }
        String code = body.code().trim();
        Instant now = Instant.now();
        ModelRegistryItem item = new ModelRegistryItem(
                newId(), code, body.name().trim(),
                body.provider() == null ? "openai-compatible" : body.provider().trim(),
                body.baseUrl() == null ? "" : body.baseUrl().trim(),
                body.contextWindow() == null ? 128000 : body.contextWindow(),
                body.maxTokens() == null ? 16384 : body.maxTokens(),
                body.enabled() == null || body.enabled(),
                now, now
        );
        if (modelRepository.findByCode(code).isPresent()) {
            modelRepository.updateByCode(item);
        } else {
            modelRepository.insert(item);
        }
        ModelRegistryItem latest = modelRepository.findByCode(code).orElse(item);
        return ApiResponse.success(toModelResponse(latest));
    }

    @Operation(summary = "创建或更新数据集")
    @PostMapping("/datasets/upsert")
    public ApiResponse<DatasetRegistryResponse> upsertDataset(@RequestBody ApiRequest<UpsertDatasetRegistryRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertDatasetRegistryRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || isBlank(body.code()) || isBlank(body.name())) {
            return ApiResponse.error(400, "code/name 不能为空");
        }
        String code = body.code().trim();
        Instant now = Instant.now();
        DatasetRegistryItem item = new DatasetRegistryItem(
                newId(), code, body.name().trim(),
                body.description() == null ? "" : body.description().trim(),
                body.sourceType() == null ? "mixed" : body.sourceType().trim(),
                body.sourceRef() == null ? "" : body.sourceRef().trim(),
                body.docCount() == null ? 0 : Math.max(0, body.docCount()),
                body.enabled() == null || body.enabled(),
                now, now
        );
        if (datasetRepository.findByCode(code).isPresent()) {
            datasetRepository.updateByCode(item);
        } else {
            datasetRepository.insert(item);
        }
        DatasetRegistryItem latest = datasetRepository.findByCode(code).orElse(item);
        return ApiResponse.success(toDatasetResponse(latest));
    }

    private AgentRegistryResponse toAgentResponse(AgentRegistryItem a) {
        String wt = a.welcomeTitle() == null ? "" : a.welcomeTitle().trim();
        String wi = a.welcomeIntro() == null ? "" : a.welcomeIntro().trim();
        String p1 = a.presetChat1() == null ? "" : a.presetChat1().trim();
        String p2 = a.presetChat2() == null ? "" : a.presetChat2().trim();
        String p3 = a.presetChat3() == null ? "" : a.presetChat3().trim();
        String hermesProfile = a.hermesProfile() == null ? "" : a.hermesProfile().trim();
        return new AgentRegistryResponse(
                a.id(), a.code(), a.name(), a.category(), a.description(),
                a.icon(), a.modelCode(), wt, wi, p1, p2, p3,
                hermesProfile, a.priority(), a.enabled()
        );
    }

    private ModelRegistryResponse toModelResponse(ModelRegistryItem m) {
        return new ModelRegistryResponse(
                m.id(), m.code(), m.name(), m.provider(), m.baseUrl(),
                m.contextWindow(), m.maxTokens(), m.enabled()
        );
    }

    private DatasetRegistryResponse toDatasetResponse(DatasetRegistryItem d) {
        return new DatasetRegistryResponse(
                d.id(), d.code(), d.name(), d.description(), d.sourceType(),
                d.sourceRef(), d.docCount(), d.enabled()
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 请求字段为 null 时沿用库中旧值；新建时旧值为空串。 */
    private static String mergeNullableText(String fromBody, Optional<String> previous) {
        if (fromBody != null) {
            return fromBody.trim();
        }
        return previous.map(String::trim).orElse("");
    }

    private static void requireAgentAdmin() {
        if (!UserContext.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可新建、修改或删除智能体");
        }
    }
}

