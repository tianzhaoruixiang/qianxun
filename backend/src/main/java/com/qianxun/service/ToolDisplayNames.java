package com.qianxun.service;

import com.qianxun.domain.ToolDisplayName;
import com.qianxun.llm.ClaudeCodeToolCatalog;
import com.qianxun.repo.ToolDisplayNameRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 工具英文名 → 中文名。只使用 Claude Code 官方名，可由库表覆盖。
 */
@Service
public class ToolDisplayNames {

    private final ToolDisplayNameRepository repository;
    /** 精确键 → 中文名 */
    private volatile Map<String, String> overrides = Map.of();
    /** 小写键 → 中文名（忽略大小写命中） */
    private volatile Map<String, String> overridesLower = Map.of();

    public ToolDisplayNames(ToolDisplayNameRepository repository) {
        this.repository = repository;
        refresh();
    }

    public void refresh() {
        Map<String, String> next = new LinkedHashMap<>();
        Map<String, String> nextLower = new LinkedHashMap<>();
        // Claude Code 优先（流式事件真实工具名）
        for (ToolDisplayName row : ClaudeCodeToolCatalog.seedRows()) {
            put(next, nextLower, row.toolCode(), row.displayName());
        }
        try {
            for (ToolDisplayName row : repository.listOrderBySort()) {
                if (row.toolCode() != null && !row.toolCode().isBlank()
                        && row.displayName() != null && !row.displayName().isBlank()) {
                    put(next, nextLower, row.toolCode().trim(), row.displayName().trim());
                }
            }
        } catch (Exception ignored) {
            // 启动早期表可能尚未建好，回退内置目录
        }
        this.overrides = Map.copyOf(next);
        this.overridesLower = Map.copyOf(nextLower);
    }

    private static void put(Map<String, String> exact, Map<String, String> lower, String code, String name) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            return;
        }
        exact.put(code, name);
        lower.put(code.toLowerCase(Locale.ROOT), name);
    }

    public String displayName(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return "工具";
        }
        String code = toolCode.trim();
        String mapped = overrides.get(code);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        mapped = overridesLower.get(code.toLowerCase(Locale.ROOT));
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        String claude = ClaudeCodeToolCatalog.displayName(code);
        if (claude != null) {
            return claude;
        }
        return ClaudeCodeToolCatalog.fallbackDisplayName(code);
    }

    public String iconKind(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return "gear";
        }
        String claude = ClaudeCodeToolCatalog.iconKind(toolCode);
        if (claude != null) {
            return claude;
        }
        return ClaudeCodeToolCatalog.fallbackIconKind(toolCode);
    }

    public Map<String, String> allDisplayNames() {
        return overrides;
    }
}
