package com.qianxun.service;

import com.qianxun.config.QianxunProperties;
import com.qianxun.context.UserContext;
import com.qianxun.llm.ClaudeCodeToolCatalog;
import com.qianxun.llm.ClaudeCodeToolsets;
import com.qianxun.llm.HermesAgentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 工具市场：只列出 Claude Code 工具集，开关写入网关 {@code qianxun-toolsets.json}。
 * 不会混入 Hermes 内置工具。
 */
@Service
public class HermesToolsetService {

    private static final Logger log = LoggerFactory.getLogger(HermesToolsetService.class);

    /** 网关哨兵：写入 api_server 列表后，不再默认挂上全部 MCP 工具。 */
    static final String NO_MCP = "no_mcp";

    /**
     * 对话缺省白名单：默认打开 Claude Code 目录中的全部工具集。
     * 用户在工具市场关闭的项写入 disabled，不会被这份缺省列表重新打开。
     */
    static final List<String> DEFAULT_CHAT_ENABLED = ClaudeCodeToolsets.DEFAULT_ENABLED;

    /** 不再默认禁用任何目录内工具集。 */
    static final List<String> ALWAYS_DISABLED = List.of();

    public record ToolItem(String name, String displayName, String iconKind, boolean enabled) {}

    public record ToolsetView(
            String name,
            String label,
            String description,
            String platform,
            String platformLabel,
            boolean enabled,
            boolean configured,
            List<ToolItem> tools
    ) {}

    public record ChatToolsetPlan(List<String> enabled, List<String> disabled, List<String> known) {}

    private final HermesAgentClient hermes;
    private final QianxunProperties properties;

    public HermesToolsetService(HermesAgentClient hermes, QianxunProperties properties) {
        this.hermes = hermes;
        this.properties = properties;
    }

    public List<ToolsetView> list(String profile) {
        List<HermesAgentClient.ToolsetInfo> raw = hermes.listToolsets(UserContext.getCurrentUserId(), profile);
        Map<String, HermesAgentClient.ToolsetInfo> by = new LinkedHashMap<>();
        if (raw != null) {
            for (HermesAgentClient.ToolsetInfo t : raw) {
                if (t != null && ClaudeCodeToolsets.isKnown(t.name())) {
                    by.put(t.name().trim().toLowerCase(Locale.ROOT), t);
                }
            }
        }
        List<ToolsetView> out = new ArrayList<>();
        for (ClaudeCodeToolsets.Def d : ClaudeCodeToolsets.CATALOG) {
            HermesAgentClient.ToolsetInfo t = by.get(d.name().toLowerCase(Locale.ROOT));
            boolean on = t == null || t.enabled();
            HermesAgentClient.ToolsetInfo src = new HermesAgentClient.ToolsetInfo(
                    d.name(),
                    d.label(),
                    d.description(),
                    t != null && t.platform() != null && !t.platform().isBlank() ? t.platform() : "cli",
                    t != null && t.platformLabel() != null && !t.platformLabel().isBlank() ? t.platformLabel() : "CLI",
                    on,
                    true,
                    d.displayTools());
            out.add(toView(src, on));
        }
        return out;
    }

    public HermesAgentClient.ToolsetWriteResult toggle(String profile, String name, boolean enabled) {
        if (!ClaudeCodeToolsets.isKnown(name)) {
            String n = name == null ? "" : name.trim();
            return new HermesAgentClient.ToolsetWriteResult(false, n, enabled, "未知的 Claude Code 工具集");
        }
        return hermes.toggleToolset(UserContext.getCurrentUserId(), profile, name, enabled);
    }

    /**
     * 对话走 Dashboard/CLI，不再把开关同步进 {@code platform_toolsets.api_server}。
     * 长程目标时补开 CLI 侧 file / todo / kanban（若清单中存在且当前关闭）。
     */
    public void ensureChatGatewaySynced(String profile) {
        ensureChatGatewaySynced(profile, false);
    }

    /**
     * @param longHorizon 为 true 时，在 CLI 工具清单里补开 file / todo / kanban（若存在）。
     */
    public void ensureChatGatewaySynced(String profile, boolean longHorizon) {
        if (!hermes.isConfigured() || !longHorizon) {
            return;
        }
        List<HermesAgentClient.ToolsetInfo> listed = hermes.listToolsets(UserContext.getCurrentUserId(),profile);
        if (listed == null || listed.isEmpty()) {
            return;
        }
        for (String extra : List.of("file", "todo", "kanban")) {
            for (HermesAgentClient.ToolsetInfo t : listed) {
                if (t != null && extra.equalsIgnoreCase(t.name()) && !t.enabled()) {
                    HermesAgentClient.ToolsetWriteResult r = hermes.toggleToolset(UserContext.getCurrentUserId(),profile, t.name(), true);
                    if (!r.ok()) {
                        log.debug("长程任务补开工具集失败 profile={} name={}: {}",
                                syncKey(profile), t.name(), r.message());
                    }
                }
            }
        }
    }

    void syncChatGateway(String profile, List<HermesAgentClient.ToolsetInfo> listed) {
        syncChatGateway(profile, listed, false);
    }

    void syncChatGateway(String profile, List<HermesAgentClient.ToolsetInfo> listed, boolean longHorizon) {
        if (listed == null || listed.isEmpty()) {
            return;
        }
        ChatToolsetPlan plan = planChatGateway(listed, longHorizon);
        ChatToolsetPlan toWrite = plan;
        HermesAgentClient.ConfigWriteResult r = hermes.syncChatGatewayToolsets(UserContext.getCurrentUserId(),
                profile, apiServerConfigList(toWrite.enabled()), toWrite.disabled(), toWrite.known());
        if (!r.ok()) {
            log.warn("对话工具集同步失败 profile={}: {}", syncKey(profile), r.message());
            return;
        }
        List<String> extras = extraGatewayToolsets(profile, toWrite.enabled());
        if (!extras.isEmpty()) {
            LinkedHashSet<String> disabled = new LinkedHashSet<>(toWrite.disabled());
            disabled.addAll(extras);
            toWrite = new ChatToolsetPlan(toWrite.enabled(), List.copyOf(disabled), toWrite.known());
            r = hermes.syncChatGatewayToolsets(UserContext.getCurrentUserId(),
                    profile, apiServerConfigList(toWrite.enabled()), toWrite.disabled(), toWrite.known());
            if (!r.ok()) {
                log.warn("对话工具集二次同步失败 profile={}: {}", syncKey(profile), r.message());
                return;
            }
            extras = extraGatewayToolsets(profile, toWrite.enabled());
        }
        if (!"already-synced".equals(r.message())) {
            log.info("已把工具集开关同步到对话网关 profile={} enabled={} disabled={} extras={}",
                    syncKey(profile), toWrite.enabled().size(), toWrite.disabled().size(), extras.size());
        }
        if (!extras.isEmpty()) {
            log.warn("对话网关仍多出未关闭工具集 profile={} extras={}", syncKey(profile), extras);
        }
    }

    List<String> extraGatewayToolsets(String profile, List<String> enabled) {
        return extrasBeyond(hermes.listChatGatewayEnabledToolsets(UserContext.getCurrentUserId(),profile), enabled);
    }

    static List<String> extrasBeyond(List<String> live, List<String> enabled) {
        if (live == null || live.isEmpty()) {
            return List.of();
        }
        Set<String> want = namesLower(enabled);
        LinkedHashSet<String> extras = new LinkedHashSet<>();
        for (String name : live) {
            if (name == null || name.isBlank() || NO_MCP.equalsIgnoreCase(name)) {
                continue;
            }
            if (!want.contains(name.trim().toLowerCase(Locale.ROOT))) {
                extras.add(name.trim());
            }
        }
        return List.copyOf(extras);
    }

    static ChatToolsetPlan planChatGateway(List<HermesAgentClient.ToolsetInfo> listed) {
        return planChatGateway(listed, false);
    }

    static ChatToolsetPlan planChatGateway(List<HermesAgentClient.ToolsetInfo> listed, boolean longHorizon) {
        LinkedHashSet<String> enabled = new LinkedHashSet<>();
        LinkedHashSet<String> disabled = new LinkedHashSet<>();
        LinkedHashSet<String> known = new LinkedHashSet<>();
        LinkedHashSet<String> present = new LinkedHashSet<>();
        if (listed != null) {
            for (HermesAgentClient.ToolsetInfo t : listed) {
                if (t == null || !isAtomicToolset(t.name())) {
                    continue;
                }
                String name = t.name().trim();
                present.add(name);
                known.add(name);
                disabled.add(name);
            }
        }
        // 对话白名单只认 Claude Code 目录。市场开关通过网关 disabled 列表生效。
        for (String extra : DEFAULT_CHAT_ENABLED) {
            if (isAlwaysDisabled(extra)) {
                continue;
            }
            enabled.add(canonicalPresent(present, extra));
            disabled.removeIf(n -> extra.equalsIgnoreCase(n));
        }
        if (longHorizon) {
            for (String extra : List.of("file", "todo", "kanban")) {
                if (present.stream().anyMatch(n -> extra.equalsIgnoreCase(n))) {
                    enabled.add(canonicalPresent(present, extra));
                    disabled.removeIf(n -> extra.equalsIgnoreCase(n));
                }
            }
        }
        for (String banned : ALWAYS_DISABLED) {
            enabled.removeIf(n -> banned.equalsIgnoreCase(n));
            disabled.add(canonicalPresent(present, banned));
            known.add(canonicalPresent(present, banned));
        }
        disabled.removeIf(n -> enabled.stream().anyMatch(e -> e.equalsIgnoreCase(n)));
        return new ChatToolsetPlan(List.copyOf(enabled), List.copyOf(disabled), List.copyOf(known));
    }

    List<String> apiServerConfigList(List<String> enabled) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (enabled != null) {
            for (String n : enabled) {
                if (n != null && !n.isBlank() && !NO_MCP.equalsIgnoreCase(n)) {
                    out.add(n.trim());
                }
            }
        }
        List<String> list = new ArrayList<>(out);
        if (properties.getClaude().isAppendNoMcp()) {
            list.add(NO_MCP);
        }
        return List.copyOf(list);
    }

    static List<String> apiServerConfigList(List<String> enabled, boolean appendNoMcp) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (enabled != null) {
            for (String n : enabled) {
                if (n != null && !n.isBlank() && !NO_MCP.equalsIgnoreCase(n)) {
                    out.add(n.trim());
                }
            }
        }
        List<String> list = new ArrayList<>(out);
        if (appendNoMcp) {
            list.add(NO_MCP);
        }
        return List.copyOf(list);
    }

    static boolean isAlwaysDisabled(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String key = name.trim();
        return ALWAYS_DISABLED.stream().anyMatch(n -> n.equalsIgnoreCase(key));
    }

    private static String canonicalPresent(Set<String> present, String want) {
        for (String n : present) {
            if (want.equalsIgnoreCase(n)) {
                return n;
            }
        }
        return want;
    }

    static boolean isAtomicToolset(String name) {
        return ClaudeCodeToolsets.isKnown(name);
    }

    void patchChatGatewayEnabled(String profile, String name, boolean enabled) {
        String key = name == null ? "" : name.trim();
        if (key.isBlank() || NO_MCP.equalsIgnoreCase(key)) {
            return;
        }
        HermesAgentClient.ChatGatewayToolsets current = hermes.readChatGatewayToolsets(UserContext.getCurrentUserId(),profile);
        LinkedHashSet<String> on = new LinkedHashSet<>();
        if (current.ok() && current.enabled() != null) {
            for (String n : current.enabled()) {
                if (n != null && !n.isBlank() && !NO_MCP.equalsIgnoreCase(n)) {
                    on.add(n.trim());
                }
            }
        }
        if (enabled) {
            on.removeIf(n -> n.equalsIgnoreCase(key));
            on.add(key);
        } else {
            on.removeIf(n -> n.equalsIgnoreCase(key));
        }
        List<HermesAgentClient.ToolsetInfo> listed = hermes.listToolsets(UserContext.getCurrentUserId(),profile);
        LinkedHashSet<String> known = new LinkedHashSet<>();
        LinkedHashSet<String> disabled = new LinkedHashSet<>();
        if (listed != null) {
            for (HermesAgentClient.ToolsetInfo t : listed) {
                if (t == null || !isAtomicToolset(t.name())) {
                    continue;
                }
                String n = t.name().trim();
                known.add(n);
                if (on.stream().noneMatch(e -> e.equalsIgnoreCase(n))) {
                    disabled.add(n);
                }
            }
        }
        hermes.syncChatGatewayToolsets(UserContext.getCurrentUserId(),
                profile, apiServerConfigList(List.copyOf(on)), List.copyOf(disabled), List.copyOf(known));
    }

    Set<String> resolveChatEnabledNames(String profile) {
        HermesAgentClient.ChatGatewayToolsets gw = hermes.readChatGatewayToolsets(UserContext.getCurrentUserId(),profile);
        if (gw.ok() && gw.apiServerConfigured()) {
            return chatEnabledSet(gw.enabled());
        }
        return chatEnabledSet(hermes.listChatGatewayEnabledToolsets(UserContext.getCurrentUserId(),profile));
    }

    static ChatToolsetPlan mergeLongHorizon(List<String> apiServer, List<HermesAgentClient.ToolsetInfo> listed) {
        LinkedHashSet<String> present = new LinkedHashSet<>();
        LinkedHashSet<String> known = new LinkedHashSet<>();
        if (listed != null) {
            for (HermesAgentClient.ToolsetInfo t : listed) {
                if (t == null || !isAtomicToolset(t.name())) {
                    continue;
                }
                String n = t.name().trim();
                present.add(n);
                known.add(n);
            }
        }
        LinkedHashSet<String> enabled = new LinkedHashSet<>();
        for (String n : chatEnabledSet(apiServer)) {
            enabled.add(canonicalPresent(present, n));
        }
        for (String extra : List.of("file", "todo", "kanban")) {
            if (present.stream().anyMatch(n -> extra.equalsIgnoreCase(n))) {
                enabled.add(canonicalPresent(present, extra));
            }
        }
        LinkedHashSet<String> disabled = new LinkedHashSet<>();
        for (String n : known) {
            if (enabled.stream().noneMatch(e -> e.equalsIgnoreCase(n))) {
                disabled.add(n);
            }
        }
        return new ChatToolsetPlan(List.copyOf(enabled), List.copyOf(disabled), List.copyOf(known));
    }

    static Set<String> chatEnabledSet(List<String> apiServer) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (apiServer == null) {
            return out;
        }
        for (String n : apiServer) {
            if (n == null || n.isBlank() || NO_MCP.equalsIgnoreCase(n)) {
                continue;
            }
            out.add(n.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    static boolean chatEnabled(String name, Set<String> apiServerEnabled) {
        if (name == null || name.isBlank() || NO_MCP.equalsIgnoreCase(name)) {
            return false;
        }
        return apiServerEnabled != null && apiServerEnabled.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    static ToolsetView toView(HermesAgentClient.ToolsetInfo t) {
        return toView(t, t.enabled());
    }

    static ToolsetView toView(HermesAgentClient.ToolsetInfo t, boolean enabled) {
        List<ToolItem> tools = new ArrayList<>();
        if (t.tools() != null) {
            for (String code : t.tools()) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                String name = code.trim();
                tools.add(new ToolItem(
                        name,
                        displayNameOf(name),
                        iconKindOf(name),
                        enabled));
            }
        }
        return new ToolsetView(
                t.name(),
                t.label() == null || t.label().isBlank() ? t.name() : t.label(),
                t.description() == null ? "" : t.description(),
                t.platform() == null ? "" : t.platform(),
                t.platformLabel() == null ? "" : t.platformLabel(),
                enabled,
                t.configured(),
                List.copyOf(tools)
        );
    }

    private static String displayNameOf(String name) {
        return ClaudeCodeToolCatalog.fallbackDisplayName(name);
    }

    private static String iconKindOf(String name) {
        return ClaudeCodeToolCatalog.fallbackIconKind(name);
    }

    private static Set<String> namesLower(List<String> names) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (names == null) {
            return out;
        }
        for (String n : names) {
            if (n != null && !n.isBlank()) {
                out.add(n.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String syncKey(String profile) {
        if (profile == null || profile.isBlank()) {
            return "default";
        }
        String t = profile.trim();
        if ("default".equalsIgnoreCase(t) || "hermes-agent".equalsIgnoreCase(t)) {
            return "default";
        }
        return t;
    }
}
