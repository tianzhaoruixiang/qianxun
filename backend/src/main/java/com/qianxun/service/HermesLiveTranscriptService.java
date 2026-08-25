package com.qianxun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.context.UserContext;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.llm.HermesWorkspaceSandbox;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读取 Hermes 后台 {@code delegate_task} 的 live transcript
 *（{@code &lt;profileHome&gt;/cache/delegation/live/&lt;deleg_id&gt;/task-N.log}）。
 * <p>
 * 日志为运维视角、行内截断；适合实时观测，不是 SessionDB 全文。
 */
@Service
public class HermesLiveTranscriptService {

    private static final Pattern DELEG_ID = Pattern.compile("^deleg_[a-f0-9]{6,16}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_LOG = Pattern.compile("^task-(\\d+)\\.log$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY = Pattern.compile(
            "(?i)^(?:log\\s+)?(deleg_[a-f0-9]{6,16})(?:\\s+(?:task[-_\\s]*)?(\\d+))?\\s*$"
    );
    private static final int DEFAULT_LIST_LIMIT = 8;
    private static final int DEFAULT_TAIL_CHARS = 12_000;
    private static final int MAX_TAIL_CHARS = 80_000;

    private final HermesAgentClient hermes;
    private final ObjectMapper objectMapper;

    public HermesLiveTranscriptService(HermesAgentClient hermes, ObjectMapper objectMapper) {
        this.hermes = hermes;
        this.objectMapper = objectMapper;
    }

    public record TaskLogInfo(int index, String path, String goal, String status, Long size) {}

    public record DelegationInfo(
            String delegationId,
            String path,
            String started,
            String completed,
            int taskCount,
            List<TaskLogInfo> tasks
    ) {}

    public record LogContent(
            boolean ok,
            String delegationId,
            Integer taskIndex,
            String path,
            String content,
            String message
    ) {}

    public record DeleteResult(boolean ok, String delegationId, String path, String message, boolean notFound) {}

    public record CancelResult(boolean ok, String delegationId, String message, boolean notFound) {}

    public String liveRoot(String profile) {
        return HermesWorkspaceSandbox.profileHome(UserContext.getCurrentUserId(), profile) + "/cache/delegation/live";
    }

    public List<DelegationInfo> listRecent(String profile, int limit) {
        int lim = limit <= 0 ? DEFAULT_LIST_LIMIT : Math.min(limit, 30);
        String root = liveRoot(profile);
        HermesAgentClient.ManagedDirList listed = hermes.listManagedDirectory(UserContext.getCurrentUserId(), profile, root);
        if (!listed.ok()) {
            return List.of();
        }
        List<HermesAgentClient.ManagedDirEntry> dirs = listed.entries().stream()
                .filter(HermesAgentClient.ManagedDirEntry::directory)
                .filter(e -> DELEG_ID.matcher(safeName(e.name())).matches())
                .sorted(Comparator.comparing((HermesAgentClient.ManagedDirEntry e) -> safeName(e.name())).reversed())
                .limit(lim)
                .toList();
        List<DelegationInfo> out = new ArrayList<>();
        for (HermesAgentClient.ManagedDirEntry dir : dirs) {
            String id = safeName(dir.name());
            DelegationInfo info = loadDelegation(profile, id);
            if (info != null) {
                out.add(info);
            }
        }
        return List.copyOf(out);
    }

    public DelegationInfo loadDelegation(String profile, String delegationId) {
        String id = sanitizeDelegId(delegationId);
        if (id.isBlank()) {
            return null;
        }
        String dir = liveRoot(profile) + "/" + id;
        String started = "";
        String completed = "";
        List<TaskLogInfo> tasks = new ArrayList<>();
        HermesAgentClient.DownloadedFile manifest = hermes.downloadManagedFile(UserContext.getCurrentUserId(), profile, dir + "/manifest.json", false);
        if (manifest.ok() && manifest.bytes() != null && manifest.bytes().length > 0) {
            try {
                JsonNode root = objectMapper.readTree(manifest.bytes());
                started = text(root, "started");
                completed = text(root, "completed");
                JsonNode arr = root.path("tasks");
                if (arr.isArray()) {
                    for (JsonNode n : arr) {
                        int idx = n.path("index").asInt(-1);
                        String log = text(n, "log");
                        if (idx < 0 && !log.isBlank()) {
                            Matcher m = TASK_LOG.matcher(filename(log));
                            if (m.matches()) {
                                idx = Integer.parseInt(m.group(1));
                            }
                        }
                        if (idx < 0) {
                            continue;
                        }
                        String path = log.isBlank() ? dir + "/task-" + idx + ".log" : log;
                        tasks.add(new TaskLogInfo(idx, path, text(n, "goal"), text(n, "status"), null));
                    }
                }
            } catch (Exception ignored) {
                /* fall through to directory listing */
            }
        }
        if (tasks.isEmpty()) {
            HermesAgentClient.ManagedDirList listed = hermes.listManagedDirectory(UserContext.getCurrentUserId(), profile, dir);
            if (listed.ok()) {
                for (HermesAgentClient.ManagedDirEntry e : listed.entries()) {
                    if (e.directory()) {
                        continue;
                    }
                    Matcher m = TASK_LOG.matcher(safeName(e.name()));
                    if (!m.matches()) {
                        continue;
                    }
                    int idx = Integer.parseInt(m.group(1));
                    String path = e.path() == null || e.path().isBlank() ? dir + "/" + e.name() : e.path();
                    tasks.add(new TaskLogInfo(idx, path, "", "", e.size()));
                }
            }
        }
        tasks.sort(Comparator.comparingInt(TaskLogInfo::index));
        if (tasks.isEmpty() && started.isBlank() && completed.isBlank()) {
            // 目录存在但无日志时仍返回空壳，便于调用方展示 id
            HermesAgentClient.ManagedDirList listed = hermes.listManagedDirectory(UserContext.getCurrentUserId(), profile, dir);
            if (!listed.ok()) {
                return null;
            }
        }
        return new DelegationInfo(id, dir, started, completed, tasks.size(), List.copyOf(tasks));
    }

    public DeleteResult deleteDelegation(String profile, String delegationId) {
        String id = sanitizeDelegId(delegationId);
        if (id.isBlank()) {
            return new DeleteResult(false, "", "", "delegationId 无效", false);
        }
        String dir = liveRoot(profile) + "/" + id;
        HermesLiveTranscriptService.DelegationInfo info = loadDelegation(profile, id);
        if (info == null) {
            return new DeleteResult(false, id, dir, "委派不存在", true);
        }
        HermesAgentClient.ManagedDeleteResult r = hermes.deleteManagedPath(UserContext.getCurrentUserId(), dir);
        if (!r.ok()) {
            return new DeleteResult(false, id, dir, r.message(), false);
        }
        return new DeleteResult(true, id, dir, "已删除", false);
    }

    public CancelResult cancelDelegation(String profile, String delegationId) {
        String id = sanitizeDelegId(delegationId);
        if (id.isBlank()) {
            return new CancelResult(false, "", "delegationId 无效", false);
        }
        String dir = liveRoot(profile) + "/" + id;
        DelegationInfo info = loadDelegation(profile, id);
        if (info == null) {
            return new CancelResult(false, id, "委派不存在", true);
        }
        try {
            java.util.LinkedHashMap<String, Object> manifest = new java.util.LinkedHashMap<>();
            manifest.put("delegationId", id);
            manifest.put("started", info.started());
            manifest.put("completed", java.time.Instant.now().toString());
            manifest.put("status", "cancelled");
            manifest.put("tasks", info.tasks().stream().map(t -> java.util.Map.of(
                    "index", t.index(),
                    "log", t.path(),
                    "goal", t.goal() == null ? "" : t.goal(),
                    "status", "cancelled"
            )).toList());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
            HermesAgentClient.ManagedWriteResult w = hermes.writeManagedFile(
                    UserContext.getCurrentUserId(), dir + "/manifest.json", json.getBytes(StandardCharsets.UTF_8));
            if (!w.ok()) {
                return new CancelResult(false, id, w.message(), false);
            }
            hermes.signalDelegationCancel(UserContext.getCurrentUserId(), profile, id);
            return new CancelResult(true, id, "已取消", false);
        } catch (Exception ex) {
            return new CancelResult(false, id, "取消失败: " + ex.getMessage(), false);
        }
    }

    public LogContent readTaskLog(String profile, String delegationId, Integer taskIndex, int maxChars) {
        String id = sanitizeDelegId(delegationId);
        if (id.isBlank()) {
            return new LogContent(false, "", null, "", "", "delegation_id 无效");
        }
        DelegationInfo info = loadDelegation(profile, id);
        if (info == null || info.tasks().isEmpty()) {
            return new LogContent(false, id, taskIndex, "", "", "未找到该委派的 live transcript");
        }
        List<TaskLogInfo> targets = new ArrayList<>();
        if (taskIndex == null) {
            targets.addAll(info.tasks());
        } else {
            info.tasks().stream().filter(t -> t.index() == taskIndex).findFirst().ifPresent(targets::add);
            if (targets.isEmpty()) {
                return new LogContent(false, id, taskIndex, "", "", "未找到 task-" + taskIndex + ".log");
            }
        }
        int budget = maxChars <= 0 ? DEFAULT_TAIL_CHARS : Math.min(maxChars, MAX_TAIL_CHARS);
        StringBuilder sb = new StringBuilder();
        String lastPath = "";
        for (TaskLogInfo task : targets) {
            HermesAgentClient.DownloadedFile file = hermes.downloadManagedFile(UserContext.getCurrentUserId(), profile, task.path(), false);
            if (!file.ok() || file.bytes() == null) {
                if (sb.isEmpty()) {
                    return new LogContent(false, id, task.index(), task.path(), "",
                            file.message() == null || file.message().isBlank() ? "读取日志失败" : file.message());
                }
                continue;
            }
            lastPath = task.path();
            String text = new String(file.bytes(), StandardCharsets.UTF_8);
            if (targets.size() > 1) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("----- task-").append(task.index()).append(".log");
                if (task.goal() != null && !task.goal().isBlank()) {
                    sb.append(" · ").append(oneLine(task.goal(), 80));
                }
                sb.append(" -----\n");
            }
            sb.append(tail(text, budget / Math.max(1, targets.size())));
        }
        return new LogContent(true, id, taskIndex, lastPath, sb.toString(), "");
    }

    /**
     * 拼进聊天：无查询时列最近委派；有 {@code deleg_xxx} / {@code log deleg_xxx [n]} 时输出日志正文。
     */
    public String formatChatAppendix(String profile, String query) {
        ParsedQuery q = parseQuery(query);
        if (q.delegationId() != null) {
            LogContent log = readTaskLog(profile, q.delegationId(), q.taskIndex(), DEFAULT_TAIL_CHARS);
            if (!log.ok()) {
                return "\n\n## Live transcript\n\n" + log.message() + "\n";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n## Live transcript · `").append(log.delegationId()).append("`");
            if (log.taskIndex() != null) {
                sb.append(" · task-").append(log.taskIndex());
            }
            sb.append("\n\n```text\n");
            sb.append(log.content());
            if (!log.content().endsWith("\n")) {
                sb.append('\n');
            }
            sb.append("```\n");
            return sb.toString();
        }
        List<DelegationInfo> recent = listRecent(profile, DEFAULT_LIST_LIMIT);
        if (recent.isEmpty()) {
            return "\n\n## Live transcript\n\n当前 profile 下暂无 live transcript（尚无后台子智能体落盘日志）。\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## Live transcript（最近 ")
                .append(recent.size())
                .append(" 批）\n\n");
        sb.append("发送 `/task log <delegation_id>` 或 `/task log <delegation_id> <taskIndex>` 查看详情。\n\n");
        for (DelegationInfo d : recent) {
            sb.append("- `").append(d.delegationId()).append("`");
            if (d.taskCount() > 0) {
                sb.append(" · ").append(d.taskCount()).append(" 个子任务");
            }
            if (d.completed() != null && !d.completed().isBlank()) {
                sb.append(" · 完成 ").append(d.completed());
            } else if (d.started() != null && !d.started().isBlank()) {
                sb.append(" · 开始 ").append(d.started());
            }
            sb.append('\n');
            for (TaskLogInfo t : d.tasks()) {
                sb.append("  - task-").append(t.index());
                if (t.status() != null && !t.status().isBlank()) {
                    sb.append(" · ").append(t.status());
                }
                if (t.goal() != null && !t.goal().isBlank()) {
                    sb.append(" · ").append(oneLine(t.goal(), 72));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public record ParsedQuery(String delegationId, Integer taskIndex) {}

    public static ParsedQuery parseQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            return new ParsedQuery(null, null);
        }
        // 去掉可能残留的斜杠前缀
        q = q.replaceFirst("(?i)^/(?:task|tasks|agents)\\s+", "").trim();
        q = q.replaceFirst("(?i)^(status|状态|任务)\\s*", "").trim();
        if (q.isBlank()) {
            return new ParsedQuery(null, null);
        }
        Matcher m = QUERY.matcher(q);
        if (!m.matches()) {
            return new ParsedQuery(null, null);
        }
        String id = sanitizeDelegId(m.group(1));
        Integer idx = null;
        if (m.group(2) != null && !m.group(2).isBlank()) {
            try {
                idx = Integer.parseInt(m.group(2));
            } catch (NumberFormatException ignored) {
                idx = null;
            }
        }
        return new ParsedQuery(id.isBlank() ? null : id, idx);
    }

    static String sanitizeDelegId(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return DELEG_ID.matcher(s).matches() ? s : "";
    }

    static String tail(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        int start = text.length() - maxChars;
        int nl = text.indexOf('\n', start);
        if (nl > start && nl < text.length() - 1) {
            start = nl + 1;
        }
        return "…（前文已省略 " + start + " 字符）\n" + text.substring(start);
    }

    private static String safeName(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim().replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        return s;
    }

    private static String filename(String path) {
        return safeName(path);
    }

    private static String text(JsonNode n, String field) {
        if (n == null) {
            return "";
        }
        String v = n.path(field).asText("");
        return v == null ? "" : v.trim();
    }

    private static String oneLine(String s, int max) {
        String t = s == null ? "" : String.join(" ", s.trim().split("\\s+"));
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }
}
