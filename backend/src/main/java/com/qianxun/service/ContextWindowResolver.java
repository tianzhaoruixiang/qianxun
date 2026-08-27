package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.llm.OpenAiModelsClient;
import com.qianxun.repo.ModelRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 进入对话时按当前选中模型解析上下文窗口：优先上游 {@code /models} 实时声明，其次模型注册表。
 */
@Component
public class ContextWindowResolver {

    private static final Logger log = LoggerFactory.getLogger(ContextWindowResolver.class);

    private final ModelRegistryRepository modelRegistryRepository;
    private final HermesAgentClient hermesAgentClient;
    private final OpenAiModelsClient openAiModelsClient;
    private final SystemSettingsService systemSettingsService;

    public ContextWindowResolver(
            ModelRegistryRepository modelRegistryRepository,
            HermesAgentClient hermesAgentClient,
            OpenAiModelsClient openAiModelsClient,
            SystemSettingsService systemSettingsService
    ) {
        this.modelRegistryRepository = modelRegistryRepository;
        this.hermesAgentClient = hermesAgentClient;
        this.openAiModelsClient = openAiModelsClient;
        this.systemSettingsService = systemSettingsService;
    }

    /**
     * 网关占位模型（种子 hermes-agent 等）的 context_window 不能代表子智能体真实模型。
     */
    public static boolean isGatewayStubModel(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            return false;
        }
        String key = codeOrName.trim();
        return "hermes-agent".equalsIgnoreCase(key) || "qianxun-default".equalsIgnoreCase(key)
                || "claude-code".equalsIgnoreCase(key);
    }

    public int resolve(String modelCode, String upstreamModel, String hermesProfile) {
        Set<String> ids = candidateModelIds(modelCode, upstreamModel);
        int live = windowFromLiveApi(ids);
        if (live > 0) {
            return live;
        }
        if (!isGatewayStubModel(modelCode)) {
            int fromSelected = windowFromRegistry(modelCode);
            if (fromSelected > 0) {
                return fromSelected;
            }
        }
        int fromUpstream = windowFromRegistry(upstreamModel);
        if (fromUpstream > 0) {
            return fromUpstream;
        }
        for (String id : ids) {
            if (id.equals(modelCode) || id.equals(upstreamModel)) {
                continue;
            }
            int w = windowFromRegistry(id);
            if (w > 0) {
                return w;
            }
        }
        int fromProfile = windowFromHermesProfile(hermesProfile);
        if (fromProfile > 0) {
            return fromProfile;
        }
        if (isGatewayStubModel(modelCode)) {
            return windowFromRegistry(modelCode);
        }
        return 0;
    }

    /** 列表/展示用：profile 自带窗口，否则按运行时模型解析（上游 /models 优先）。 */
    public Integer enrichHermesProfileWindow(Integer profileWindow, String profileModel) {
        if (profileWindow != null && profileWindow > 0) {
            return profileWindow;
        }
        int resolved = resolve(profileModel, profileModel, null);
        return resolved > 0 ? resolved : null;
    }

    /** 当前系统配置的对话模型最大上下文窗口（进入对话前即可展示）。 */
    public int resolveRuntimeModelWindow() {
        String model = systemSettingsService.resolvedClaudeChatModel();
        return resolve(model, model, null);
    }

    private Set<String> candidateModelIds(String modelCode, String upstreamModel) {
        Set<String> ids = new LinkedHashSet<>();
        if (!isGatewayStubModel(modelCode) && notBlank(modelCode)) {
            ids.add(modelCode.trim());
        }
        if (notBlank(upstreamModel)) {
            ids.add(upstreamModel.trim());
        }
        String configured = systemSettingsService.resolvedClaudeChatModel();
        if (notBlank(configured)) {
            ids.add(configured.trim());
        }
        return ids;
    }

    private int windowFromLiveApi(Set<String> modelIds) {
        if (modelIds.isEmpty()) {
            return 0;
        }
        String settingsBase = blankToEmpty(systemSettingsService.resolvedOpenaiBaseUrl());
        String settingsKey = blankToEmpty(systemSettingsService.resolvedOpenaiApiKey());
        for (String id : modelIds) {
            int w = queryWindow(settingsBase, settingsKey, id);
            if (w > 0) {
                return w;
            }
            Optional<ModelRegistryItem> item = findRegistryItem(id);
            if (item.isEmpty()) {
                continue;
            }
            String itemBase = blankToEmpty(item.get().baseUrl());
            if (itemBase.isEmpty() || itemBase.equalsIgnoreCase(settingsBase)) {
                continue;
            }
            w = queryWindow(itemBase, settingsKey, id);
            if (w > 0) {
                return w;
            }
        }
        return 0;
    }

    private int queryWindow(String baseUrl, String apiKey, String modelId) {
        if (baseUrl.isEmpty()) {
            return 0;
        }
        try {
            int w = openAiModelsClient.findContextWindow(baseUrl, apiKey, modelId);
            if (w > 0) {
                log.debug("上游 /models 声明 {} 上下文窗口 {}", modelId, w);
            }
            return w;
        } catch (Exception ex) {
            log.debug("查询上游上下文窗口失败 model={}: {}", modelId, ex.toString());
            return 0;
        }
    }

    private int windowFromHermesProfile(String hermesProfile) {
        if (hermesProfile == null || hermesProfile.isBlank() || !hermesAgentClient.isConfigured()) {
            return 0;
        }
        String want = hermesProfile.trim();
        try {
            List<HermesAgentClient.HermesProfile> profiles = hermesAgentClient.listProfiles(UserContext.getCurrentUserId());
            for (HermesAgentClient.HermesProfile p : profiles) {
                if (p.name() == null || !p.name().equalsIgnoreCase(want)) {
                    continue;
                }
                if (p.contextWindow() != null && p.contextWindow() > 0) {
                    return p.contextWindow();
                }
                int byModel = windowFromRegistry(p.model());
                if (byModel > 0) {
                    return byModel;
                }
            }
        } catch (Exception ex) {
            log.debug("读取 Hermes profile 上下文窗口失败: {}", ex.toString());
        }
        return 0;
    }

    int windowFromRegistry(String codeOrName) {
        return findRegistryItem(codeOrName)
                .filter(item -> item.contextWindow() > 0)
                .map(ModelRegistryItem::contextWindow)
                .orElse(0);
    }

    private Optional<ModelRegistryItem> findRegistryItem(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            return Optional.empty();
        }
        String key = codeOrName.trim();
        Optional<ModelRegistryItem> byCode = modelRegistryRepository.findByCode(key);
        if (byCode.isPresent()) {
            return byCode;
        }
        return modelRegistryRepository.findByNameIgnoreCase(key);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
