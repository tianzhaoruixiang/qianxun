package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import com.qianxun.storage.HermesGeneratedDocuments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 智能体管理面 HTTP 客户端：对接独立容器中的 Claude Code SDK 网关
 *（profile / CLAUDE.md / 技能 / 工具集 / 工作区文件）。
 */
@Component
public class HermesAgentClient {

    private static final Logger log = LoggerFactory.getLogger(HermesAgentClient.class);

    public record HermesProfile(
            String name,
            String description,
            String model,
            boolean active,
            String path,
            Integer contextWindow
    ) {}

    public record CreateProfileResult(boolean ok, String name, String path, String message, boolean alreadyExists) {}

    public record SoulResult(boolean ok, String content, boolean exists, String message) {}

    public record PublishTemplateResult(boolean ok, String name, String path, String message) {}

    public record DeleteProfileResult(boolean ok, String name, boolean alreadyGone, String message) {}

    public record DownloadedFile(boolean ok, byte[] bytes, String filename, String message) {}

    public record SkillInfo(
            String name,
            String description,
            String category,
            boolean enabled,
            String provenance
    ) {}

    public record SkillContentResult(boolean ok, String name, String content, String path, String message) {}

    public record SkillWriteResult(boolean ok, String name, String path, String message) {}

    public record ToolsetInfo(
            String name,
            String label,
            String description,
            String platform,
            String platformLabel,
            boolean enabled,
            boolean configured,
            List<String> tools
    ) {}

    public record ToolsetWriteResult(boolean ok, String name, boolean enabled, String message) {}

    public record ManagedDirEntry(String name, String path, boolean directory, Long size, Long mtimeMs) {}

    public record ManagedDirList(boolean ok, String path, List<ManagedDirEntry> entries, String message) {}

    public record ManagedWriteResult(boolean ok, String path, String message) {}

    public record ManagedMkdirResult(boolean ok, String path, String message) {}

    public record ConfigWriteResult(boolean ok, String message) {}

    public record ManagedDeleteResult(boolean ok, String path, String message) {}

    public record McpServerInfo(
            String name,
            String command,
            List<String> args,
            Map<String, String> env,
            boolean enabled,
            String description,
            String transport,
            String url
    ) {}

    public record McpWriteResult(boolean ok, String name, String message) {}

    public record PluginInfo(
            String name,
            String path,
            String version,
            boolean enabled,
            String description,
            Map<String, Object> manifest
    ) {}

    public record PluginWriteResult(boolean ok, String name, String message) {}

    public record ChatGatewayToolsets(
            boolean ok,
            List<String> enabled,
            List<String> disabled,
            boolean apiServerConfigured,
            String message
    ) {}

    private final ObjectMapper objectMapper;
    private final QianxunProperties properties;
    private final HttpClient http;

    public HermesAgentClient(ObjectMapper objectMapper, QianxunProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return properties.getClaude().isEnabled() && !origin().isBlank();
    }

    public HttpClient httpClient() {
        return http;
    }

    public String mintWsTicket() {
        throw new IllegalStateException("Claude Code 运行器使用 HTTP 流，不提供 WebSocket ticket");
    }

    public List<HermesProfile> listProfiles(String userId) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            HttpResponse<String> res = send("GET", withScope(origin() + "/api/profiles", userId, null), null);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                log.debug("GET /api/profiles HTTP {}: {}", res.statusCode(), truncate(res.body()));
                return List.of();
            }
            JsonNode root = objectMapper.readTree(blankToObj(res.body()));
            JsonNode arr = root.path("profiles");
            if (!arr.isArray() && root.isArray()) {
                arr = root;
            }
            List<HermesProfile> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String name = firstText(n, "name");
                    if (name.isBlank()) {
                        continue;
                    }
                    out.add(new HermesProfile(
                            name,
                            firstText(n, "description"),
                            firstText(n, "model"),
                            n.path("active").asBoolean(false),
                            firstText(n, "path"),
                            firstInt(n, "context_window", "contextWindow")
                    ));
                }
            }
            return List.copyOf(out);
        } catch (Exception ex) {
            log.debug("列出 profile 失败: {}", ex.toString());
            return List.of();
        }
    }

    public CreateProfileResult createProfile(String userId, String rawName, String description) {
        String name = ClaudeCodePaths.normalizeProfileName(rawName);
        if (name.isBlank()) {
            return new CreateProfileResult(false, "", "", "profile 名称无效", false);
        }
        if (!isConfigured()) {
            return new CreateProfileResult(false, name, "", "未启用或未配置智能体运行器", false);
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("name", name);
            body.put("description", description == null ? "" : description);
            putScope(body, userId, null);
            HttpResponse<String> res = send("POST", origin() + "/api/profiles", objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new CreateProfileResult(
                    ok,
                    firstText(n, "name").isBlank() ? name : firstText(n, "name"),
                    firstText(n, "path"),
                    firstText(n, "message"),
                    n.path("alreadyExists").asBoolean(false)
            );
        } catch (Exception ex) {
            return new CreateProfileResult(false, name, "", "创建 profile 失败: " + ex.getMessage(), false);
        }
    }

    public SoulResult getSoul(String userId, String rawName) {
        String name = ClaudeCodePaths.normalizeProfileName(rawName);
        if (name.isBlank()) {
            return new SoulResult(false, "", false, "profile 名称无效");
        }
        if (!isConfigured()) {
            return new SoulResult(false, "", false, "未启用或未配置智能体运行器");
        }
        try {
            HttpResponse<String> res = send("GET",
                    withScope(origin() + "/api/profiles/" + encode(name) + "/soul", userId, null), null);
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new SoulResult(ok, firstText(n, "content"), n.path("exists").asBoolean(false), firstText(n, "message"));
        } catch (Exception ex) {
            return new SoulResult(false, "", false, "读取 CLAUDE.md 失败: " + ex.getMessage());
        }
    }

    public SoulResult putSoul(String userId, String rawName, String content) {
        String name = ClaudeCodePaths.normalizeProfileName(rawName);
        if (name.isBlank()) {
            return new SoulResult(false, "", false, "profile 名称无效");
        }
        if (!isConfigured()) {
            return new SoulResult(false, "", false, "未启用或未配置智能体运行器");
        }
        String text = content == null ? "" : content;
        if (text.length() > 80_000) {
            return new SoulResult(false, "", false, "SOUL.md 过长（最多 80000 字）");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("content", text);
            putScope(body, userId, null);
            HttpResponse<String> res = send("PUT",
                    withScope(origin() + "/api/profiles/" + encode(name) + "/soul", userId, null),
                    objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new SoulResult(ok, text, n.path("exists").asBoolean(ok), firstText(n, "message"));
        } catch (Exception ex) {
            return new SoulResult(false, "", false, "写入 CLAUDE.md 失败: " + ex.getMessage());
        }
    }

    /**
     * 将指定用户 profile 全量发布到 {@code /opt/data/_templates/profiles/{name}/}，
     * 供普通用户首次对话时自动复制（灵魂、技能、工具集等）。
     */
    public PublishTemplateResult publishProfileTemplate(String userId, String rawName) {
        String name = ClaudeCodePaths.normalizeProfileName(rawName);
        if (name.isBlank()) {
            return new PublishTemplateResult(false, "", "", "profile 名称无效");
        }
        if (!isConfigured()) {
            return new PublishTemplateResult(false, name, "", "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            putScope(body, userId, null);
            HttpResponse<String> res = send("POST",
                    withScope(origin() + "/api/profiles/" + encode(name) + "/publish-template", userId, null),
                    objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new PublishTemplateResult(
                    ok,
                    firstText(n, "name").isBlank() ? name : firstText(n, "name"),
                    firstText(n, "path"),
                    firstText(n, "message")
            );
        } catch (Exception ex) {
            return new PublishTemplateResult(false, name, "", "发布模板失败: " + ex.getMessage());
        }
    }

    public DeleteProfileResult deleteProfile(String userId, String rawName) {
        String name = ClaudeCodePaths.normalizeProfileName(rawName);
        if (name.isBlank()) {
            return new DeleteProfileResult(true, "", false, "无 profile，跳过");
        }
        if ("default".equals(name)) {
            return new DeleteProfileResult(true, name, false, "跳过默认 profile");
        }
        if (!isConfigured()) {
            return new DeleteProfileResult(true, name, false, "未启用运行器，跳过");
        }
        try {
            HttpResponse<String> res = send("DELETE",
                    withScope(origin() + "/api/profiles/" + encode(name), userId, null), null);
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new DeleteProfileResult(ok, name, n.path("alreadyGone").asBoolean(false), firstText(n, "message"));
        } catch (Exception ex) {
            return new DeleteProfileResult(false, name, false, "删除 profile 失败: " + ex.getMessage());
        }
    }

    public void ensureProfileApiKey(String rawName) {
        /* Anthropic 密钥只存在于 Claude Code 容器 */
    }

    public static String sanitizeProfileName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        s = s.replaceAll("^-+", "").replaceAll("-+$", "");
        if (s.isBlank()) {
            return "";
        }
        if (s.charAt(0) >= '0' && s.charAt(0) <= '9') {
            s = "p-" + s;
        }
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s;
    }

    public String normalizeProfileName(String raw) {
        return ClaudeCodePaths.normalizeProfileName(raw);
    }

    public List<SkillInfo> listSkills(String userId, String profile) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            HttpResponse<String> res = send("GET", withScope(origin() + "/api/skills", userId, profile), null);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(blankToObj(res.body()));
            JsonNode arr = root.path("skills");
            if (!arr.isArray() && root.isArray()) {
                arr = root;
            }
            List<SkillInfo> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String name = firstText(n, "name");
                    if (name.isBlank()) {
                        continue;
                    }
                    out.add(new SkillInfo(
                            name,
                            firstText(n, "description"),
                            firstText(n, "category"),
                            n.path("enabled").asBoolean(true),
                            firstText(n, "provenance")
                    ));
                }
            }
            return List.copyOf(out);
        } catch (Exception ex) {
            log.debug("列出技能失败: {}", ex.toString());
            return List.of();
        }
    }

    public SkillContentResult getSkillContent(String userId, String profile, String skillName) {
        String name = sanitizeSkillName(skillName);
        if (name.isBlank()) {
            return new SkillContentResult(false, "", "", "", "技能名称无效");
        }
        if (!isConfigured()) {
            return new SkillContentResult(false, name, "", "", "未启用或未配置智能体运行器");
        }
        try {
            String url = withScope(origin() + "/api/skills/content?name=" + encode(name), userId, profile);
            HttpResponse<String> res = send("GET", url, null);
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new SkillContentResult(
                    ok,
                    firstText(n, "name").isBlank() ? name : firstText(n, "name"),
                    firstText(n, "content"),
                    firstText(n, "path"),
                    firstText(n, "message")
            );
        } catch (Exception ex) {
            return new SkillContentResult(false, name, "", "", "读取技能失败: " + ex.getMessage());
        }
    }

    public SkillWriteResult putSkillContent(String userId, String profile, String skillName, String content) {
        return writeSkill("PUT", origin() + "/api/skills/content", userId, profile, skillName, content, null);
    }

    public SkillWriteResult createSkill(String userId, String profile, String skillName, String content, String category) {
        String name = sanitizeSkillName(skillName);
        if (name.isBlank()) {
            return new SkillWriteResult(false, "", "", "技能名称无效");
        }
        String text = content == null ? "" : content;
        if (text.isBlank()) {
            return new SkillWriteResult(false, name, "", "SKILL.md 不能为空");
        }
        return writeSkill("POST", origin() + "/api/skills", userId, profile, name, text, category);
    }

    public SkillWriteResult toggleSkill(String userId, String profile, String skillName, boolean enabled) {
        String name = sanitizeSkillName(skillName);
        if (name.isBlank()) {
            return new SkillWriteResult(false, "", "", "技能名称无效");
        }
        if (!isConfigured()) {
            return new SkillWriteResult(false, name, "", "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("name", name);
            body.put("enabled", enabled);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("PUT", origin() + "/api/skills/toggle", objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new SkillWriteResult(ok, name, firstText(n, "path"), firstText(n, "message"));
        } catch (Exception ex) {
            return new SkillWriteResult(false, name, "", "切换技能失败: " + ex.getMessage());
        }
    }

    public List<ToolsetInfo> listToolsets(String userId, String profile) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            HttpResponse<String> res = send("GET", withScope(origin() + "/api/tools/toolsets", userId, profile), null);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(blankToObj(res.body()));
            JsonNode arr = root.path("toolsets");
            if (!arr.isArray() && root.isArray()) {
                arr = root;
            }
            List<ToolsetInfo> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String name = firstText(n, "name");
                    if (name.isBlank()) {
                        continue;
                    }
                    List<String> tools = stringList(n.path("tools"));
                    out.add(new ToolsetInfo(
                            name,
                            firstText(n, "label"),
                            firstText(n, "description"),
                            firstText(n, "platform"),
                            firstText(n, "platformLabel"),
                            n.path("enabled").asBoolean(false),
                            n.path("configured").asBoolean(true),
                            tools
                    ));
                }
            }
            return List.copyOf(out);
        } catch (Exception ex) {
            log.debug("列出工具集失败: {}", ex.toString());
            return List.of();
        }
    }

    public ToolsetWriteResult toggleToolset(String userId, String profile, String toolsetName, boolean enabled) {
        String name = sanitizeToolsetName(toolsetName);
        if (name.isBlank()) {
            return new ToolsetWriteResult(false, "", false, "工具集名称无效");
        }
        if (!isConfigured()) {
            return new ToolsetWriteResult(false, name, enabled, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("enabled", enabled);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("PUT", origin() + "/api/tools/toolsets/" + encode(name),
                    objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            boolean on = n.path("enabled").asBoolean(enabled);
            return new ToolsetWriteResult(ok, name, on, firstText(n, "message"));
        } catch (Exception ex) {
            return new ToolsetWriteResult(false, name, enabled, "切换工具集失败: " + ex.getMessage());
        }
    }

    public ConfigWriteResult syncChatGatewayToolsets(String userId, String profile, List<String> enabled, List<String> disabled) {
        return syncChatGatewayToolsets(userId, profile, enabled, disabled, List.of());
    }

    public ConfigWriteResult syncChatGatewayToolsets(
            String userId, String profile, List<String> enabled, List<String> disabled, List<String> known
    ) {
        if (!isConfigured()) {
            return new ConfigWriteResult(false, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("enabled", enabled == null ? List.of() : enabled);
            body.put("disabled", disabled == null ? List.of() : disabled);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("PUT", withScope(origin() + "/api/config", userId, profile),
                    objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            String msg = firstText(n, "message");
            return new ConfigWriteResult(ok, msg);
        } catch (Exception ex) {
            return new ConfigWriteResult(false, "同步对话工具集失败: " + ex.getMessage());
        }
    }

    public List<String> listChatGatewayEnabledToolsets(String userId, String profile) {
        ChatGatewayToolsets gw = readChatGatewayToolsets(userId, profile);
        if (gw.ok() && gw.enabled() != null && !gw.enabled().isEmpty()) {
            return gw.enabled().stream()
                    .filter(n -> n != null && !n.isBlank() && !"no_mcp".equalsIgnoreCase(n))
                    .toList();
        }
        return List.copyOf(ClaudeCodeToolsets.DEFAULT_ENABLED);
    }

    List<String> parseEnabledGatewayToolsets(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        JsonNode arr = root;
        if (root.isObject()) {
            if (root.path("data").isArray()) {
                arr = root.path("data");
            } else if (root.path("toolsets").isArray()) {
                arr = root.path("toolsets");
            } else if (root.path("items").isArray()) {
                arr = root.path("items");
            }
        }
        List<String> out = new ArrayList<>();
        if (!arr.isArray()) {
            return out;
        }
        for (JsonNode n : arr) {
            if (n != null && n.path("enabled").asBoolean(false)) {
                String name = firstText(n, "name", "id", "key");
                if (!name.isBlank()) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    public ChatGatewayToolsets readChatGatewayToolsets(String userId, String profile) {
        if (!isConfigured()) {
            return new ChatGatewayToolsets(false, List.of(), List.of(), false, "未启用或未配置智能体运行器");
        }
        try {
            HttpResponse<String> res = send("GET", withScope(origin() + "/api/config", userId, profile), null);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return new ChatGatewayToolsets(false, List.of(), List.of(), false,
                        "读取配置失败 HTTP " + res.statusCode());
            }
            JsonNode root = objectMapper.readTree(blankToObj(res.body()));
            List<String> enabled = stringList(root.path("enabled"));
            List<String> disabled = stringList(root.path("disabled"));
            boolean configured = root.path("apiServerConfigured").asBoolean(root.path("enabled").isArray());
            return new ChatGatewayToolsets(root.path("ok").asBoolean(true), enabled, disabled, configured, "");
        } catch (Exception ex) {
            return new ChatGatewayToolsets(false, List.of(), List.of(), false, ex.getMessage());
        }
    }

    public static String sanitizeToolsetName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        s = s.replaceAll("^-+", "").replaceAll("-+$", "");
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    public ManagedMkdirResult ensureManagedDirectory(String userId, String profile, String absPath) {
        String p = absPath == null ? "" : absPath.trim();
        if (p.isBlank()) {
            return new ManagedMkdirResult(false, "", "路径为空");
        }
        if (!isConfigured()) {
            return new ManagedMkdirResult(false, p, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("path", p);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("POST", origin() + "/api/files/mkdir", objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new ManagedMkdirResult(ok, firstText(n, "path").isBlank() ? p : firstText(n, "path"), firstText(n, "message"));
        } catch (Exception ex) {
            return new ManagedMkdirResult(false, p, "创建目录失败: " + ex.getMessage());
        }
    }

    public ManagedDirList listManagedDirectory(String userId, String absPath) {
        return listManagedDirectory(userId, null, absPath, false);
    }

    public ManagedDirList listManagedDirectory(String userId, String profile, String absPath) {
        return listManagedDirectory(userId, profile, absPath, false);
    }

    public ManagedDirList listManagedDirectory(String userId, String profile, String absPath, boolean recursive) {
        String p = absPath == null ? "" : absPath.trim();
        if (p.isBlank()) {
            return new ManagedDirList(false, "", List.of(), "路径为空");
        }
        if (!isConfigured()) {
            return new ManagedDirList(false, p, List.of(), "未启用或未配置智能体运行器");
        }
        try {
            String url = withScope(
                    origin() + "/api/files?path=" + encodeQueryPath(p) + (recursive ? "&recursive=true" : ""),
                    userId, profile);
            HttpResponse<String> res = send("GET", url, null);
            JsonNode root = objectMapper.readTree(blankToObj(res.body()));
            if (!root.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300)) {
                return new ManagedDirList(false, p, List.of(), firstText(root, "message"));
            }
            List<ManagedDirEntry> entries = new ArrayList<>();
            JsonNode arr = root.path("entries");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String name = firstText(n, "name");
                    if (name.isBlank()) {
                        continue;
                    }
                    boolean dir = n.path("is_directory").asBoolean(
                            n.path("isDirectory").asBoolean(n.path("directory").asBoolean(false)));
                    Long size = n.path("size").isNumber() ? n.path("size").asLong() : null;
                    Long mtimeMs = n.path("mtimeMs").isNumber()
                            ? n.path("mtimeMs").asLong()
                            : (n.path("mtime_ms").isNumber() ? n.path("mtime_ms").asLong() : null);
                    String path = firstText(n, "path");
                    entries.add(new ManagedDirEntry(name, path.isBlank() ? name : path, dir, size, mtimeMs));
                }
            }
            String listed = firstText(root, "path");
            return new ManagedDirList(true, listed.isBlank() ? p : listed, entries, "");
        } catch (Exception ex) {
            return new ManagedDirList(false, p, List.of(), "列出目录失败: " + ex.getMessage());
        }
    }

    public ManagedWriteResult writeManagedFile(String userId, String absPath, byte[] bytes) {
        String p = absPath == null ? "" : absPath.trim();
        if (p.isBlank()) {
            return new ManagedWriteResult(false, "", "路径为空");
        }
        if (!isConfigured()) {
            return new ManagedWriteResult(false, p, "未启用或未配置智能体运行器");
        }
        byte[] data = bytes == null ? new byte[0] : bytes;
        if (data.length > 8 * 1024 * 1024) {
            return new ManagedWriteResult(false, p, "单文件超过 8MiB");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("path", p);
            body.put("contentBase64", java.util.Base64.getEncoder().encodeToString(data));
            putScope(body, userId, null);
            HttpResponse<String> res = send("POST", origin() + "/api/files/write", objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new ManagedWriteResult(ok, firstText(n, "path").isBlank() ? p : firstText(n, "path"), firstText(n, "message"));
        } catch (Exception ex) {
            return new ManagedWriteResult(false, p, "写入文件失败: " + ex.getMessage());
        }
    }

    public DownloadedFile downloadManagedFile(String userId, String profile, String path) {
        return downloadManagedFile(userId, profile, path, true);
    }

    public DownloadedFile downloadGeneratedDocument(String userId, String profile, String path) {
        DownloadedFile last = new DownloadedFile(false, new byte[0], filenameOf(path), "路径为空");
        for (String candidate : HermesGeneratedDocuments.downloadCandidates(path, userId)) {
            last = downloadManagedFile(userId, profile, candidate, false);
            if (last.ok()) {
                return last;
            }
        }
        return last;
    }

    public DownloadedFile downloadManagedFile(String userId, String profile, String path, boolean fallbackToBasename) {
        String raw = path == null ? "" : path.trim();
        if (raw.isBlank()) {
            return new DownloadedFile(false, new byte[0], "", "路径为空");
        }
        if (!isConfigured()) {
            return new DownloadedFile(false, new byte[0], filenameOf(raw), "未启用或未配置智能体运行器");
        }
        try {
            String url = withScope(origin() + "/api/files/download?path=" + encodeQueryPath(raw), userId, profile);
            HttpResponse<byte[]> res = sendBytes("GET", url);
            if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null && res.body().length > 0) {
                return new DownloadedFile(true, res.body(), filenameOf(raw), "");
            }
            if (fallbackToBasename && res.statusCode() == 404 && raw.contains("/")) {
                return downloadManagedFile(userId, profile, filenameOf(raw), false);
            }
            String msg = res.body() == null ? "" : new String(res.body(), StandardCharsets.UTF_8);
            return new DownloadedFile(false, new byte[0], filenameOf(raw),
                    "读取文件失败 HTTP " + res.statusCode() + ": " + truncate(msg));
        } catch (Exception ex) {
            return new DownloadedFile(false, new byte[0], filenameOf(raw), "读取文件失败: " + ex.getMessage());
        }
    }

    public ManagedDeleteResult deleteManagedPath(String userId, String absPath) {
        String p = absPath == null ? "" : absPath.trim();
        if (p.isBlank()) {
            return new ManagedDeleteResult(false, "", "路径为空");
        }
        if (!isConfigured()) {
            return new ManagedDeleteResult(false, p, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("path", p);
            putScope(body, userId, null);
            HttpResponse<String> res = send("POST", origin() + "/api/files/delete", objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new ManagedDeleteResult(ok, firstText(n, "path").isBlank() ? p : firstText(n, "path"), firstText(n, "message"));
        } catch (Exception ex) {
            return new ManagedDeleteResult(false, p, "删除失败: " + ex.getMessage());
        }
    }

    public List<McpServerInfo> listMcpServers(String userId, String profile) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            HttpResponse<String> res = send("GET", withScope(origin() + "/api/mcp/servers", userId, profile), null);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(blankToObj(res.body()));
            JsonNode arr = root.path("servers");
            List<McpServerInfo> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String name = firstText(n, "name");
                    if (name.isBlank()) {
                        continue;
                    }
                    out.add(new McpServerInfo(
                            name,
                            firstText(n, "command"),
                            stringList(n.path("args")),
                            stringMap(n.path("env")),
                            n.path("enabled").asBoolean(true),
                            firstText(n, "description"),
                            firstText(n, "transport").isBlank() ? "stdio" : firstText(n, "transport"),
                            firstText(n, "url")
                    ));
                }
            }
            return List.copyOf(out);
        } catch (Exception ex) {
            log.debug("列出 MCP Server 失败: {}", ex.toString());
            return List.of();
        }
    }

    public McpWriteResult upsertMcpServer(String userId, String profile, String name, String command,
            List<String> args, Map<String, String> env, boolean enabled, String description,
            String transport, String url) {
        if (!isConfigured()) {
            return new McpWriteResult(false, name, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("name", name);
            body.put("command", command == null ? "" : command);
            body.put("args", args == null ? List.of() : args);
            body.put("env", env == null ? Map.of() : env);
            body.put("enabled", enabled);
            body.put("description", description == null ? "" : description);
            body.put("transport", transport == null ? "stdio" : transport);
            body.put("url", url == null ? "" : url);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("POST", origin() + "/api/mcp/servers", objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new McpWriteResult(ok, firstText(n, "name").isBlank() ? name : firstText(n, "name"), firstText(n, "message"));
        } catch (Exception ex) {
            return new McpWriteResult(false, name, "写入 MCP Server 失败: " + ex.getMessage());
        }
    }

    public McpWriteResult toggleMcpServer(String userId, String profile, String name, boolean enabled) {
        if (!isConfigured()) {
            return new McpWriteResult(false, name, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("enabled", enabled);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("PUT",
                    withScope(origin() + "/api/mcp/servers/" + encode(name) + "/toggle", userId, profile),
                    objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new McpWriteResult(ok, name, firstText(n, "message"));
        } catch (Exception ex) {
            return new McpWriteResult(false, name, "切换 MCP Server 失败: " + ex.getMessage());
        }
    }

    public McpWriteResult deleteMcpServer(String userId, String profile, String name) {
        if (!isConfigured()) {
            return new McpWriteResult(false, name, "未启用或未配置智能体运行器");
        }
        try {
            HttpResponse<String> res = send("DELETE",
                    withScope(origin() + "/api/mcp/servers/" + encode(name), userId, profile), null);
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new McpWriteResult(ok, name, firstText(n, "message"));
        } catch (Exception ex) {
            return new McpWriteResult(false, name, "删除 MCP Server 失败: " + ex.getMessage());
        }
    }

    public List<PluginInfo> listPlugins(String userId, String profile) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            HttpResponse<String> res = send("GET", withScope(origin() + "/api/plugins", userId, profile), null);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(blankToObj(res.body()));
            JsonNode arr = root.path("plugins");
            List<PluginInfo> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String name = firstText(n, "name");
                    if (name.isBlank()) {
                        continue;
                    }
                    Map<String, Object> manifest = objectMapper.convertValue(
                            n.path("manifest"), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    out.add(new PluginInfo(
                            name,
                            firstText(n, "path"),
                            firstText(n, "version"),
                            n.path("enabled").asBoolean(true),
                            firstText(n, "description"),
                            manifest == null ? Map.of() : manifest
                    ));
                }
            }
            return List.copyOf(out);
        } catch (Exception ex) {
            log.debug("列出插件失败: {}", ex.toString());
            return List.of();
        }
    }

    public PluginWriteResult upsertPlugin(String userId, String profile, String name, String path,
            String version, boolean enabled, String description, Map<String, Object> manifest) {
        if (!isConfigured()) {
            return new PluginWriteResult(false, name, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("name", name);
            body.put("path", path == null ? "" : path);
            body.put("version", version == null ? "" : version);
            body.put("enabled", enabled);
            body.put("description", description == null ? "" : description);
            body.put("manifest", manifest == null ? Map.of() : manifest);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("POST", origin() + "/api/plugins", objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new PluginWriteResult(ok, firstText(n, "name").isBlank() ? name : firstText(n, "name"), firstText(n, "message"));
        } catch (Exception ex) {
            return new PluginWriteResult(false, name, "写入插件失败: " + ex.getMessage());
        }
    }

    public PluginWriteResult deletePlugin(String userId, String profile, String name) {
        if (!isConfigured()) {
            return new PluginWriteResult(false, name, "未启用或未配置智能体运行器");
        }
        try {
            HttpResponse<String> res = send("DELETE",
                    withScope(origin() + "/api/plugins/" + encode(name), userId, profile), null);
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new PluginWriteResult(ok, name, firstText(n, "message"));
        } catch (Exception ex) {
            return new PluginWriteResult(false, name, "删除插件失败: " + ex.getMessage());
        }
    }

    public PluginWriteResult togglePlugin(String userId, String profile, String name, boolean enabled) {
        if (!isConfigured()) {
            return new PluginWriteResult(false, name, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("enabled", enabled);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("PUT",
                    withScope(origin() + "/api/plugins/" + encode(name) + "/toggle", userId, profile),
                    objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new PluginWriteResult(ok, name, firstText(n, "message"));
        } catch (Exception ex) {
            return new PluginWriteResult(false, name, "切换插件失败: " + ex.getMessage());
        }
    }

    public record GatewayStatus(boolean ok, String runner, boolean configured, String model, boolean authRequired) {}

    public Optional<GatewayStatus> getGatewayStatus() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            HttpResponse<String> res = send("GET", origin() + "/api/status", null);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            return Optional.of(new GatewayStatus(
                    n.path("ok").asBoolean(true),
                    firstText(n, "runner").isBlank() ? "claude-code" : firstText(n, "runner"),
                    n.path("configured").asBoolean(false),
                    firstText(n, "model"),
                    n.path("authRequired").asBoolean(true)
            ));
        } catch (Exception ex) {
            log.debug("读取网关状态失败: {}", ex.toString());
            return Optional.empty();
        }
    }

    public McpWriteResult signalDelegationCancel(String userId, String profile, String delegationId) {
        if (!isConfigured()) {
            return new McpWriteResult(false, delegationId, "未启用或未配置智能体运行器");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("delegationId", delegationId);
            putScope(body, userId, profile);
            HttpResponse<String> res = send("POST", origin() + "/api/delegation/cancel",
                    objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new McpWriteResult(ok, delegationId, firstText(n, "message"));
        } catch (Exception ex) {
            return new McpWriteResult(false, delegationId, "发送取消信号失败: " + ex.getMessage());
        }
    }

    /** 对话是否允许加载 profile 级 MCP（与 no_mcp 哨兵 / append-no-mcp 对齐） */
    public boolean isChatMcpAllowed(String userId, String profile) {
        if (properties.getClaude().isAppendNoMcp()) {
            return false;
        }
        ChatGatewayToolsets gw = readChatGatewayToolsets(userId, profile);
        if (!gw.ok()) {
            return true;
        }
        if (gw.enabled() != null && gw.enabled().stream().anyMatch(n -> "no_mcp".equalsIgnoreCase(n))) {
            return false;
        }
        return gw.disabled() == null || gw.disabled().stream().noneMatch(n -> "no_mcp".equalsIgnoreCase(n));
    }

    private static Map<String, String> stringMap(JsonNode n) {
        Map<String, String> out = new LinkedHashMap<>();
        if (n != null && n.isObject()) {
            n.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText("")));
        }
        return out;
    }

    public static String sanitizeSkillName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isBlank()) {
            return "";
        }
        s = s.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        s = s.replaceAll("[^A-Za-z0-9._-]+", "-");
        s = s.replaceAll("^[.-]+", "").replaceAll("[.-]+$", "");
        if (s.isBlank() || ".".equals(s) || "..".equals(s)) {
            return "";
        }
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s;
    }

    public String origin() {
        return trimSlash(properties.getClaude().getBaseUrl());
    }

    public String chatBaseUrlForProfile(String profile) {
        return origin();
    }

    private SkillWriteResult writeSkill(
            String method, String url, String userId, String profile, String skillName, String content, String category
    ) {
        String name = sanitizeSkillName(skillName);
        if (name.isBlank()) {
            return new SkillWriteResult(false, "", "", "技能名称无效");
        }
        if (!isConfigured()) {
            return new SkillWriteResult(false, name, "", "未启用或未配置智能体运行器");
        }
        String text = content == null ? "" : content;
        if (text.length() > 1_048_576) {
            return new SkillWriteResult(false, name, "", "SKILL.md 过长（最多 1MiB）");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("name", name);
            body.put("content", text);
            if (category != null && !category.isBlank()) {
                body.put("category", category.trim());
            }
            putScope(body, userId, profile);
            HttpResponse<String> res = send(method, url, objectMapper.writeValueAsString(body));
            JsonNode n = objectMapper.readTree(blankToObj(res.body()));
            boolean ok = n.path("ok").asBoolean(res.statusCode() >= 200 && res.statusCode() < 300);
            return new SkillWriteResult(ok, name, firstText(n, "path"), firstText(n, "message"));
        } catch (Exception ex) {
            return new SkillWriteResult(false, name, "", "写入技能失败: " + ex.getMessage());
        }
    }

    private HttpResponse<String> send(String method, String url, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30));
        applyAuth(b);
        if (json != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> sendBytes(String method, String url) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .method(method, HttpRequest.BodyPublishers.noBody());
        applyAuth(b);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private void applyAuth(HttpRequest.Builder b) {
        String key = properties.getClaude().getApiKey();
        if (key != null && !key.isBlank()) {
            b.header("Authorization", "Bearer " + key.trim());
        }
        String traceId = com.qianxun.context.TraceContext.get();
        if (traceId != null && !traceId.isBlank()) {
            b.header(com.qianxun.context.TraceContext.HEADER, traceId);
        }
    }

    private String withScope(String url, String userId, String profile) {
        String uid = requireUserId(userId);
        String out = url + (url.contains("?") ? "&" : "?") + "userId=" + encode(uid);
        String name = ClaudeCodePaths.normalizeProfileName(profile);
        if (!name.isBlank() && !"default".equals(name)) {
            out = out + "&profile=" + encode(name);
        }
        return out;
    }

    private void putScope(Map<String, Object> body, String userId, String profile) {
        body.put("userId", requireUserId(userId));
        putProfile(body, profile);
    }

    private String requireUserId(String userId) {
        String uid = HermesWorkspaceSandbox.sanitizeOwnerId(userId);
        if (uid.isBlank()) {
            throw new IllegalArgumentException("用户标识无效");
        }
        return uid;
    }

    private String withProfile(String url, String profile) {
        String name = ClaudeCodePaths.normalizeProfileName(profile);
        if (name.isBlank() || "default".equals(name)) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "profile=" + encode(name);
    }

    private void putProfile(Map<String, Object> body, String profile) {
        String name = ClaudeCodePaths.normalizeProfileName(profile);
        if (!name.isBlank() && !"default".equals(name)) {
            body.put("profile", name);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQueryPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path.replace('\\', '/');
        String[] parts = p.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(encode(parts[i]));
        }
        return sb.toString();
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String blankToObj(String body) {
        return body == null || body.isBlank() ? "{}" : body;
    }

    private static String firstText(JsonNode n, String... fields) {
        if (n == null) {
            return "";
        }
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && v.isTextual() && !v.asText().isBlank()) {
                return v.asText().trim();
            }
        }
        return "";
    }

    private static Integer firstInt(JsonNode n, String... fields) {
        if (n == null) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && v.isNumber()) {
                return v.asInt();
            }
        }
        return null;
    }

    private static List<String> stringList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            for (JsonNode x : n) {
                if (x != null && x.isTextual() && !x.asText().isBlank()) {
                    out.add(x.asText().trim());
                }
            }
        }
        return out;
    }

    private static String filenameOf(String path) {
        return HermesGeneratedDocuments.filenameOf(path);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 400 ? s : s.substring(0, 400);
    }
}
