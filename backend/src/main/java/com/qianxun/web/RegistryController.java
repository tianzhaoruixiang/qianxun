package com.qianxun.web;

import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.DatasetRegistryItem;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.DatasetRegistryRepository;
import com.qianxun.repo.ModelRegistryRepository;
import com.qianxun.web.dto.AgentRegistryResponse;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.DatasetRegistryResponse;
import com.qianxun.web.dto.ModelRegistryResponse;
import com.qianxun.web.dto.UpsertAgentRegistryRequest;
import com.qianxun.web.dto.UpsertDatasetRegistryRequest;
import com.qianxun.web.dto.UpsertModelRegistryRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/QianXunService/registry")
public class RegistryController {

    private final AgentRegistryRepository agentRepository;
    private final ModelRegistryRepository modelRepository;
    private final DatasetRegistryRepository datasetRepository;

    public RegistryController(
            AgentRegistryRepository agentRepository,
            ModelRegistryRepository modelRepository,
            DatasetRegistryRepository datasetRepository
    ) {
        this.agentRepository = agentRepository;
        this.modelRepository = modelRepository;
        this.datasetRepository = datasetRepository;
    }

    @PostMapping("/agents/list")
    public ApiResponse<List<AgentRegistryResponse>> listAgents(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        boolean enabledOnly = true;
        Object arg = ApiRequestSupport.jsonArg(request);
        if (arg instanceof java.util.Map<?, ?> map) {
            Object v = map.get("enabledOnly");
            if (v instanceof Boolean b) enabledOnly = b;
        }
        List<AgentRegistryResponse> data = agentRepository.list(enabledOnly).stream()
                .map(this::toAgentResponse)
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/models/list")
    public ApiResponse<List<ModelRegistryResponse>> listModels(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        boolean enabledOnly = true;
        Object arg = ApiRequestSupport.jsonArg(request);
        if (arg instanceof java.util.Map<?, ?> map) {
            Object v = map.get("enabledOnly");
            if (v instanceof Boolean b) enabledOnly = b;
        }
        List<ModelRegistryResponse> data = modelRepository.list(enabledOnly).stream()
                .map(this::toModelResponse)
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/datasets/list")
    public ApiResponse<List<DatasetRegistryResponse>> listDatasets(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        boolean enabledOnly = true;
        Object arg = ApiRequestSupport.jsonArg(request);
        if (arg instanceof java.util.Map<?, ?> map) {
            Object v = map.get("enabledOnly");
            if (v instanceof Boolean b) enabledOnly = b;
        }
        List<DatasetRegistryResponse> data = datasetRepository.list(enabledOnly).stream()
                .map(this::toDatasetResponse)
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/agents/upsert")
    public ApiResponse<AgentRegistryResponse> upsertAgent(@RequestBody ApiRequest<UpsertAgentRegistryRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertAgentRegistryRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || isBlank(body.code()) || isBlank(body.name())) {
            return ApiResponse.error(400, "code/name 不能为空");
        }
        String code = body.code().trim();
        Instant now = Instant.now();
        AgentRegistryItem item = new AgentRegistryItem(
                newId(), code, body.name().trim(),
                body.category() == null ? "general" : body.category().trim(),
                body.description() == null ? "" : body.description().trim(),
                body.icon() == null ? "" : body.icon().trim(),
                body.modelCode() == null ? "" : body.modelCode().trim(),
                body.promptTemplate() == null ? "" : body.promptTemplate().trim(),
                body.priority() == null ? 100 : body.priority(),
                body.enabled() == null || body.enabled(),
                now, now
        );
        if (agentRepository.findByCode(code).isPresent()) {
            agentRepository.updateByCode(item);
        } else {
            agentRepository.insert(item);
        }
        AgentRegistryItem latest = agentRepository.findByCode(code).orElse(item);
        return ApiResponse.success(toAgentResponse(latest));
    }

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
        return new AgentRegistryResponse(
                a.id(), a.code(), a.name(), a.category(), a.description(),
                a.icon(), a.modelCode(), a.priority(), a.enabled()
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
}

