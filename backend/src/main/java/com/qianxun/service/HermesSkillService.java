package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.storage.ArchiveUnpacker;
import com.qianxun.storage.FolderPaths;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 技能市场：对接 Hermes Dashboard {@code /api/skills}，zip 在千寻侧解压后逐文件写入技能目录。
 */
@Service
public class HermesSkillService {

    public static final int MAX_TREE_FILES = 400;
    public static final int MAX_TREE_DEPTH = 8;
    public static final long MAX_ZIP_BYTES = 32L * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 1_048_576;

    private static final Set<String> TEXT_EXT = Set.of(
            "md", "txt", "json", "yml", "yaml", "xml", "html", "css", "js", "ts",
            "py", "sh", "bash", "csv", "toml", "ini", "cfg", "properties", "svg",
            "sql", "r", "java", "go", "rs", "prompt", "jinja", "j2"
    );
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "auth.json", "auth.lock", "credentials", "config.yaml", ".env", ".envrc"
    );

    public record FileNode(String path, String name, boolean directory, Long size, boolean text) {}

    public record FileBody(boolean ok, String path, String content, boolean text, byte[] bytes, String message) {}

    public record UploadResult(boolean ok, List<String> installed, List<String> errors) {}

    public record ZipDownload(boolean ok, byte[] bytes, String filename, String message) {}

    private final HermesAgentClient hermes;

    public HermesSkillService(HermesAgentClient hermes) {
        this.hermes = hermes;
    }

    public List<HermesAgentClient.SkillInfo> list(String profile) {
        return hermes.listSkills(UserContext.getCurrentUserId(), profile);
    }

    public HermesAgentClient.SkillWriteResult toggle(String profile, String name, boolean enabled) {
        return hermes.toggleSkill(UserContext.getCurrentUserId(), profile, name, enabled);
    }

    public HermesAgentClient.SkillWriteResult saveSkillMd(String profile, String name, String content) {
        return hermes.putSkillContent(UserContext.getCurrentUserId(), profile, name, content);
    }

    public List<FileNode> tree(String profile, String skillName) {
        String name = HermesAgentClient.sanitizeSkillName(skillName);
        HermesAgentClient.SkillContentResult md = hermes.getSkillContent(UserContext.getCurrentUserId(), profile, name);
        if (!md.ok()) {
            throw new IllegalStateException(blankTo(md.message(), "读取技能失败"));
        }
        List<FileNode> out = new ArrayList<>();
        String skillDir = skillDirFromMdPath(md.path());
        if (!skillDir.isBlank()) {
            try {
                walk(skillDir, "", 0, out);
            } catch (RuntimeException ignored) {
                out.clear();
            }
        }
        if (out.stream().noneMatch(n -> "SKILL.md".equalsIgnoreCase(n.name()) && !n.directory())) {
            out.add(0, new FileNode("SKILL.md", "SKILL.md", false, (long) md.content().length(), true));
        }
        return out;
    }

    public FileBody readFile(String profile, String skillName, String relativePath) {
        String rel = sanitizeSkillRelative(relativePath);
        if (rel.isEmpty()) {
            return new FileBody(false, "", "", false, new byte[0], "文件路径无效");
        }
        if (isSensitiveName(FolderPaths.nameOf(rel))) {
            return new FileBody(false, rel, "", false, new byte[0], "拒绝读取敏感文件");
        }
        if ("SKILL.md".equalsIgnoreCase(rel)) {
            HermesAgentClient.SkillContentResult md = hermes.getSkillContent(UserContext.getCurrentUserId(), profile, skillName);
            if (!md.ok()) {
                return new FileBody(false, rel, "", true, new byte[0], md.message());
            }
            byte[] bytes = md.content().getBytes(StandardCharsets.UTF_8);
            return new FileBody(true, "SKILL.md", md.content(), true, bytes, "");
        }
        String skillDir = resolveSkillDir(profile, skillName);
        String abs = joinHermes(skillDir, rel);
        HermesAgentClient.DownloadedFile file = hermes.downloadManagedFile(UserContext.getCurrentUserId(), profile, abs, false);
        if (!file.ok()) {
            return new FileBody(false, rel, "", false, new byte[0], file.message());
        }
        boolean text = isTextPath(rel);
        String content = "";
        if (text) {
            content = new String(file.bytes(), StandardCharsets.UTF_8);
        }
        return new FileBody(true, rel, content, text, file.bytes(), "");
    }

    public HermesAgentClient.ManagedWriteResult saveFile(String profile, String skillName, String relativePath, String content) {
        String rel = sanitizeSkillRelative(relativePath);
        if (rel.isEmpty()) {
            return new HermesAgentClient.ManagedWriteResult(false, "", "文件路径无效");
        }
        if (!isTextPath(rel)) {
            return new HermesAgentClient.ManagedWriteResult(false, rel, "仅允许编辑文本文件");
        }
        if (isSensitiveName(FolderPaths.nameOf(rel))) {
            return new HermesAgentClient.ManagedWriteResult(false, rel, "拒绝写入敏感文件");
        }
        String text = content == null ? "" : content;
        if (text.length() > MAX_TEXT_CHARS) {
            return new HermesAgentClient.ManagedWriteResult(false, rel, "文件过长（最多 1MiB）");
        }
        if ("SKILL.md".equalsIgnoreCase(rel)) {
            HermesAgentClient.SkillWriteResult r = hermes.putSkillContent(UserContext.getCurrentUserId(), profile, skillName, text);
            return new HermesAgentClient.ManagedWriteResult(r.ok(), rel, r.message());
        }
        String skillDir = resolveSkillDir(profile, skillName);
        return hermes.writeManagedFile(UserContext.getCurrentUserId(), joinHermes(skillDir, rel), text.getBytes(StandardCharsets.UTF_8));
    }

    public UploadResult uploadZip(String profile, String originalFilename, byte[] zipBytes) {
        if (!hermes.isConfigured()) {
            return new UploadResult(false, List.of(), List.of("未启用或未配置 Hermes"));
        }
        if (zipBytes == null || zipBytes.length == 0) {
            return new UploadResult(false, List.of(), List.of("压缩包为空"));
        }
        if (zipBytes.length > MAX_ZIP_BYTES) {
            return new UploadResult(false, List.of(), List.of("压缩包超过 32MiB"));
        }
        List<ArchiveUnpacker.ExtractedEntry> entries;
        try {
            entries = ArchiveUnpacker.unpackZip(
                    zipBytes,
                    new ArchiveUnpacker.Limits(MAX_TREE_FILES, 6, MAX_ZIP_BYTES)
            );
        } catch (IOException ex) {
            return new UploadResult(false, List.of(), List.of("解压失败: " + ex.getMessage()));
        }
        Map<String, List<ArchiveUnpacker.ExtractedEntry>> bySkill = groupBySkill(entries, originalFilename);
        if (bySkill.isEmpty()) {
            return new UploadResult(false, List.of(), List.of("压缩包内未找到 SKILL.md"));
        }
        List<String> installed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, List<ArchiveUnpacker.ExtractedEntry>> e : bySkill.entrySet()) {
            String skill = e.getKey();
            try {
                installSkillFiles(profile, skill, e.getValue());
                installed.add(skill);
            } catch (IllegalStateException ex) {
                errors.add(skill + ": " + ex.getMessage());
            }
        }
        return new UploadResult(!installed.isEmpty(), installed, errors);
    }

    public ZipDownload downloadZip(String profile, String skillName) {
        String name = HermesAgentClient.sanitizeSkillName(skillName);
        if (name.isBlank()) {
            return new ZipDownload(false, new byte[0], "", "技能名称无效");
        }
        List<FileNode> nodes = tree(profile, name);
        if (nodes.isEmpty()) {
            HermesAgentClient.SkillContentResult md = hermes.getSkillContent(UserContext.getCurrentUserId(), profile, name);
            if (!md.ok()) {
                return new ZipDownload(false, new byte[0], name + ".zip", md.message());
            }
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                boolean any = false;
                for (FileNode node : nodes) {
                    if (node.directory()) {
                        continue;
                    }
                    FileBody body = readFile(profile, name, node.path());
                    if (!body.ok()) {
                        continue;
                    }
                    ZipEntry entry = new ZipEntry(name + "/" + node.path());
                    zos.putNextEntry(entry);
                    zos.write(body.bytes() == null ? new byte[0] : body.bytes());
                    zos.closeEntry();
                    any = true;
                }
                if (!any) {
                    HermesAgentClient.SkillContentResult md = hermes.getSkillContent(UserContext.getCurrentUserId(), profile, name);
                    if (!md.ok()) {
                        return new ZipDownload(false, new byte[0], name + ".zip", md.message());
                    }
                    zos.putNextEntry(new ZipEntry(name + "/SKILL.md"));
                    zos.write(md.content().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            return new ZipDownload(true, bos.toByteArray(), name + ".zip", "");
        } catch (IOException ex) {
            return new ZipDownload(false, new byte[0], name + ".zip", "打包失败: " + ex.getMessage());
        }
    }

    static String sanitizeSkillRelative(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String n = ArchiveUnpacker.sanitizeZipRelativePath(raw);
        if (n.isEmpty()) {
            return "";
        }
        if (n.startsWith("skills/")) {
            n = n.substring("skills/".length());
        }
        return n;
    }

    static boolean isTextPath(String relative) {
        String name = FolderPaths.nameOf(relative);
        if ("SKILL.md".equalsIgnoreCase(name)) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return TEXT_EXT.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static boolean isSensitiveName(String filename) {
        String n = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) {
            return true;
        }
        if (SENSITIVE_NAMES.contains(n) || n.startsWith(".env.")) {
            return true;
        }
        return n.endsWith(".key") || n.endsWith(".pem") || n.endsWith(".p12");
    }

    private void installSkillFiles(String profile, String skill, List<ArchiveUnpacker.ExtractedEntry> files) {
        ArchiveUnpacker.ExtractedEntry skillMd = files.stream()
                .filter(f -> "SKILL.md".equalsIgnoreCase(FolderPaths.nameOf(relativeUnderSkill(f.relativePath(), skill))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少 SKILL.md"));
        String mdRel = relativeUnderSkill(skillMd.relativePath(), skill);
        if (!"SKILL.md".equalsIgnoreCase(mdRel)) {
            throw new IllegalStateException("SKILL.md 必须位于技能根目录");
        }
        String content = new String(skillMd.bytes(), StandardCharsets.UTF_8);
        HermesAgentClient.SkillContentResult existing = hermes.getSkillContent(UserContext.getCurrentUserId(), profile, skill);
        HermesAgentClient.SkillWriteResult writeMd;
        if (existing.ok()) {
            writeMd = hermes.putSkillContent(UserContext.getCurrentUserId(), profile, skill, content);
        } else {
            writeMd = hermes.createSkill(UserContext.getCurrentUserId(), profile, skill, content, null);
            if (!writeMd.ok()) {
                writeMd = hermes.putSkillContent(UserContext.getCurrentUserId(), profile, skill, content);
            }
        }
        if (!writeMd.ok()) {
            throw new IllegalStateException(writeMd.message());
        }
        String skillDir = skillDirFromMdPath(writeMd.path());
        if (skillDir.isBlank()) {
            HermesAgentClient.SkillContentResult again = hermes.getSkillContent(UserContext.getCurrentUserId(), profile, skill);
            skillDir = skillDirFromMdPath(again.path());
        }
        if (skillDir.isBlank()) {
            throw new IllegalStateException("无法解析技能目录");
        }
        for (ArchiveUnpacker.ExtractedEntry f : files) {
            String rel = relativeUnderSkill(f.relativePath(), skill);
            if (rel.isEmpty() || "SKILL.md".equalsIgnoreCase(rel)) {
                continue;
            }
            if (isSensitiveName(FolderPaths.nameOf(rel))) {
                continue;
            }
            HermesAgentClient.ManagedWriteResult w = hermes.writeManagedFile(UserContext.getCurrentUserId(), joinHermes(skillDir, rel), f.bytes());
            if (!w.ok()) {
                throw new IllegalStateException(w.message());
            }
        }
    }

    private Map<String, List<ArchiveUnpacker.ExtractedEntry>> groupBySkill(
            List<ArchiveUnpacker.ExtractedEntry> entries,
            String zipFilename
    ) {
        List<String> skillMdPaths = new ArrayList<>();
        for (ArchiveUnpacker.ExtractedEntry e : entries) {
            if ("SKILL.md".equalsIgnoreCase(FolderPaths.nameOf(e.relativePath()))) {
                skillMdPaths.add(e.relativePath());
            }
        }
        Map<String, List<ArchiveUnpacker.ExtractedEntry>> out = new LinkedHashMap<>();
        if (skillMdPaths.isEmpty()) {
            return out;
        }
        if (skillMdPaths.size() == 1 && "SKILL.md".equalsIgnoreCase(skillMdPaths.get(0))) {
            String skill = HermesAgentClient.sanitizeSkillName(stripZip(zipFilename));
            if (skill.isBlank()) {
                skill = "uploaded-skill";
            }
            out.put(skill, entries);
            return out;
        }
        for (String mdPath : skillMdPaths) {
            String parent = FolderPaths.parentOf(mdPath);
            String skill = HermesAgentClient.sanitizeSkillName(parent.isEmpty() ? stripZip(zipFilename) : FolderPaths.nameOf(parent));
            if (skill.isBlank()) {
                continue;
            }
            String prefix = parent.isEmpty() ? "" : parent + "/";
            List<ArchiveUnpacker.ExtractedEntry> files = new ArrayList<>();
            for (ArchiveUnpacker.ExtractedEntry e : entries) {
                String rel = e.relativePath();
                if (parent.isEmpty() || rel.equals(mdPath) || rel.startsWith(prefix)) {
                    files.add(e);
                }
            }
            out.put(skill, files);
        }
        return out;
    }

    private static String relativeUnderSkill(String fullRel, String skill) {
        String n = sanitizeSkillRelative(fullRel);
        if (n.isEmpty()) {
            return "";
        }
        if (n.equals(skill) || n.startsWith(skill + "/")) {
            String rest = n.equals(skill) ? "" : n.substring(skill.length() + 1);
            return sanitizeSkillRelative(rest);
        }
        int slash = n.indexOf('/');
        if (slash > 0) {
            String first = n.substring(0, slash);
            if (skill.equalsIgnoreCase(first) || skill.equals(HermesAgentClient.sanitizeSkillName(first))) {
                return sanitizeSkillRelative(n.substring(slash + 1));
            }
        }
        return n;
    }

    private String resolveSkillDir(String profile, String skillName) {
        String name = HermesAgentClient.sanitizeSkillName(skillName);
        if (name.isBlank()) {
            throw new IllegalArgumentException("技能名称无效");
        }
        HermesAgentClient.SkillContentResult md = hermes.getSkillContent(UserContext.getCurrentUserId(), profile, name);
        if (!md.ok()) {
            throw new IllegalStateException(md.message() == null || md.message().isBlank() ? "读取技能失败" : md.message());
        }
        String dir = skillDirFromMdPath(md.path());
        if (dir.isBlank()) {
            throw new IllegalStateException("无法解析技能目录");
        }
        return dir;
    }

    private void walk(String absDir, String relPrefix, int depth, List<FileNode> out) {
        if (depth > MAX_TREE_DEPTH || out.size() >= MAX_TREE_FILES) {
            return;
        }
        HermesAgentClient.ManagedDirList listed = hermes.listManagedDirectory(UserContext.getCurrentUserId(), absDir);
        if (!listed.ok()) {
            return;
        }
        for (HermesAgentClient.ManagedDirEntry e : listed.entries()) {
            if (out.size() >= MAX_TREE_FILES) {
                return;
            }
            if (isSensitiveName(e.name())) {
                continue;
            }
            String childRel = relPrefix.isEmpty() ? e.name() : relPrefix + "/" + e.name();
            String safeRel = sanitizeSkillRelative(childRel);
            if (safeRel.isEmpty()) {
                continue;
            }
            if (e.directory()) {
                out.add(new FileNode(safeRel, e.name(), true, null, false));
                walk(e.path(), safeRel, depth + 1, out);
            } else {
                out.add(new FileNode(safeRel, e.name(), false, e.size(), isTextPath(safeRel)));
            }
        }
    }

    private static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    static String skillDirFromMdPath(String skillMdPath) {
        String p = skillMdPath == null ? "" : skillMdPath.trim().replace('\\', '/');
        if (p.isBlank()) {
            return "";
        }
        if (p.toLowerCase(Locale.ROOT).endsWith("/skill.md")) {
            return p.substring(0, p.length() - "/SKILL.md".length());
        }
        int slash = p.lastIndexOf('/');
        return slash > 0 ? p.substring(0, slash) : "";
    }

    static String joinHermes(String dir, String relative) {
        String d = dir == null ? "" : dir.trim().replace('\\', '/');
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        String r = sanitizeSkillRelative(relative);
        if (d.isBlank()) {
            return r;
        }
        return r.isEmpty() ? d : d + "/" + r;
    }

    private static String stripZip(String filename) {
        String n = filename == null ? "" : filename.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        if (n.toLowerCase(Locale.ROOT).endsWith(".zip") && n.length() > 4) {
            n = n.substring(0, n.length() - 4);
        }
        return n;
    }
}
