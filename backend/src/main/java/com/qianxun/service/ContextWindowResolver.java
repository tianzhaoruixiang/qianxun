package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.repo.ModelRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 按模型注册表、Hermes profile 的模型 / context window 解析上下文窗口。
 * 查不到时返回 0，不写死 128K。
 */
@Component
public class ContextWindowResolver {

    private static final Logger log = LoggerFactory.getLogger(ContextWindowResolver.class);

    private final ModelRegistryRepository modelRegistryRepository;
    private final HermesAgentClient hermesAgentClient;

    public ContextWindowResolver(
            ModelRegistryRepository modelRegistryRepository,
            HermesAgentClient hermesAgentClient
    ) {
        this.modelRegistryRepository = modelRegistryRepository;
        this.hermesAgentClient = hermesAgentClient;
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
        // 真实选中模型优先；跳过网关 stub，避免子智能体一直显示 128k
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
        int fromProfile = windowFromHermesProfile(hermesProfile);
        if (fromProfile > 0) {
            return fromProfile;
        }
        // stub 仅作最后兜底
        if (isGatewayStubModel(modelCode)) {
            return windowFromRegistry(modelCode);
        }
        return 0;
    }

    /** 列表/展示用：profile 自带窗口，否则按 profile.model 查注册表。 */
    public Integer enrichHermesProfileWindow(Integer profileWindow, String profileModel) {
        if (profileWindow != null && profileWindow > 0) {
            return profileWindow;
        }
        int byModel = windowFromRegistry(profileModel);
        return byModel > 0 ? byModel : null;
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
        if (codeOrName == null || codeOrName.isBlank()) {
            return 0;
        }
        String key = codeOrName.trim();
        Optional<ModelRegistryItem> byCode = modelRegistryRepository.findByCode(key);
        if (byCode.isPresent() && byCode.get().contextWindow() > 0) {
            return byCode.get().contextWindow();
        }
        Optional<ModelRegistryItem> byName = modelRegistryRepository.findByNameIgnoreCase(key);
        if (byName.isPresent() && byName.get().contextWindow() > 0) {
            return byName.get().contextWindow();
        }
        return 0;
    }
}
