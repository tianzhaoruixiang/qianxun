package com.qianxun.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Hermes 工具参数/结果中抽出智能体新写的办公文档路径。
 */
public final class HermesGeneratedDocuments {

    public static final String FOLDER = "智能体生成";
    public static final Set<String> EXTS = Set.of("xlsx", "xls", "md", "doc", "docx");

    private static final Set<String> SKIP_NAMES = Set.of(
            "soul.md", "user.md", "agents.md", "heartbeat.md", "memory.md", "boot.md"
    );
    private static final Set<String> PATH_KEYS = Set.of(
            "path", "file", "file_path", "filepath", "filename", "target", "dest",
            "output", "output_path", "output_file"
    );
    private static final Set<String> WRITE_TOOLS = Set.of(
            "write_file", "write", "edit", "patch", "create_file", "save_file",
            "bash", "notebookedit", "multiedit"
    );
    private static final Pattern PATH_IN_TEXT = Pattern.compile(
            "(?:^|[\\s\"'=:`])((?:/?[\\w.\\-\\u4e00-\\u9fff]+/)*[\\w.\\-\\u4e00-\\u9fff]+\\.(?:xlsx|xls|md|doc|docx))(?=[\\s\"'`\\])},;]|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PUBLIC_FILE_IN_TEXT = Pattern.compile(
            "(?:https?://[^\\s)]+)?(?:/QianXunService)?/data/files/public/([^\\s)\\]`\"']+\\.(?:xlsx|xls|md|doc|docx))",
            Pattern.CASE_INSENSITIVE
    );

    private HermesGeneratedDocuments() {}

    public static boolean isWriteLikeTool(String toolName) {
        String n = toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
        if (WRITE_TOOLS.contains(n) || n.endsWith("write_file") || n.endsWith("_write")) {
            return true;
        }
        // Claude Code：Write / Edit / Bash（python 写 xlsx）
        return "write".equals(n) || "edit".equals(n) || "bash".equals(n) || n.endsWith("edit");
    }

    public static boolean isDocumentFilename(String filename) {
        String ext = UserDocumentStore.extensionOf(filename);
        if (!EXTS.contains(ext)) {
            return false;
        }
        String base = filenameOf(filename).toLowerCase(Locale.ROOT);
        return !SKIP_NAMES.contains(base);
    }

    public static String filenameOf(String path) {
        if (path == null) {
            return "";
        }
        String s = path.trim().replace('\\', '/');
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    public static List<String> pathsFromTool(ObjectMapper mapper, String toolName, String args, String result) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (!isWriteLikeTool(toolName)) {
            return List.of();
        }
        collectJsonPaths(mapper, args, out);
        collectJsonPaths(mapper, result, out);
        collectTextPaths(args, out);
        collectTextPaths(result, out);
        return new ArrayList<>(out);
    }

    public static List<String> pathsFromAssistantText(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectTextPaths(text, out);
        if (text != null && !text.isBlank()) {
            Matcher m = PUBLIC_FILE_IN_TEXT.matcher(text);
            while (m.find()) {
                addPath(m.group(1), out);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Hermes 常把公开 URL 写成工作区路径，或把文件落到 {@code files/public/} / {@code /opt/data}。
     */
    public static List<String> downloadCandidates(String path) {
        return downloadCandidates(path, null);
    }

    /**
     * @param userId 千寻用户 ID。Claude Code 的 cwd 是 {@code /opt/data/{userId}/workspace}。
     */
    public static List<String> downloadCandidates(String path, String userId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (path == null || path.isBlank()) {
            return List.of();
        }
        String p = path.trim().replace('\\', '/');
        if (p.startsWith("file://")) {
            p = p.substring("file://".length());
        }
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        out.add(p);
        String base = filenameOf(p);
        String uid = userId == null ? "" : userId.trim();
        if (!base.isBlank()) {
            out.add(base);
            if (!uid.isBlank()) {
                out.add("/opt/data/" + uid + "/workspace/" + base);
                out.add("workspace/" + base);
            }
            out.add("files/public/" + base);
            out.add("/opt/data/" + base);
            out.add("/opt/data/files/public/" + base);
            out.add("/opt/data/workspace/" + base);
            // 会话沙箱：相对文件名常落在 cwd=.../workspace/qx/<sid>/
            if (p.contains("/workspace/qx/")) {
                out.add(p);
            }
        }
        String lower = p.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("/files/public/");
        if (idx >= 0) {
            String rest = p.substring(idx + "/files/public/".length());
            if (!rest.isBlank() && !rest.contains("..")) {
                out.add("files/public/" + rest);
                out.add("/opt/data/files/public/" + rest);
                out.add(filenameOf(rest));
            }
        }
        return new ArrayList<>(out);
    }

    public static String chatMarkdown(String filename, String relativeUrl) {
        String name = filename == null ? "文档" : filename.trim();
        String href = relativeUrl == null ? "" : relativeUrl.trim();
        return "\n\n[" + name + "](" + href + ")\n";
    }

    private static void collectJsonPaths(ObjectMapper mapper, String raw, Set<String> out) {
        if (raw == null || raw.isBlank() || mapper == null) {
            return;
        }
        String t = raw.trim();
        if (!(t.startsWith("{") || t.startsWith("["))) {
            return;
        }
        try {
            walk(mapper.readTree(t), out);
        } catch (Exception ignored) {
            /* 非 JSON */
        }
    }

    private static void walk(JsonNode n, Set<String> out) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return;
        }
        if (n.isObject()) {
            n.fields().forEachRemaining(e -> {
                String key = e.getKey() == null ? "" : e.getKey().trim().toLowerCase(Locale.ROOT);
                JsonNode v = e.getValue();
                if (PATH_KEYS.contains(key) && v != null && v.isTextual()) {
                    addPath(v.asText(), out);
                }
                walk(v, out);
            });
            return;
        }
        if (n.isArray()) {
            for (JsonNode c : n) {
                walk(c, out);
            }
            return;
        }
        if (n.isTextual()) {
            addPath(n.asText(), out);
        }
    }

    private static void collectTextPaths(String raw, Set<String> out) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        Matcher m = PATH_IN_TEXT.matcher(raw);
        while (m.find()) {
            addPath(m.group(1), out);
        }
    }

    private static void addPath(String raw, Set<String> out) {
        if (raw == null) {
            return;
        }
        String p = raw.trim().replace('\\', '/');
        if (p.startsWith("file://")) {
            p = p.substring("file://".length());
        }
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        if (p.isBlank() || p.contains("..")) {
            return;
        }
        if (!isDocumentFilename(p)) {
            return;
        }
        out.add(p);
    }
}
