package com.qianxun.service;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.DataFile;
import com.qianxun.storage.FilePublicLinks;
import com.qianxun.storage.FolderPaths;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 仅把<strong>本轮聊天框明确附带</strong>的文件写入发给上游的 system 消息。
 * <p>
 * 禁止：在未传附件 id 时枚举/抽取「我的网盘」近况或任意云盘文件正文。
 * 允许：用户在聊天框上传后，本轮请求携带的 {@code fileIds}（须属于当前用户）。
 */
public final class ChatFileContext {

    private ChatFileContext() {}

    public static List<String> attachedIds(List<String> fileIds) {
        LinkedHashSet<String> attached = new LinkedHashSet<>();
        if (fileIds != null) {
            for (String id : fileIds) {
                if (id != null && !id.isBlank()) {
                    attached.add(id.trim());
                }
            }
        }
        return List.copyOf(attached);
    }

    /**
     * @param attachedIds   本轮聊天附件 id；空则原样返回 messages，绝不注入网盘内容
     * @param attachedFiles 已按 id 查出、且归属校验后的文件；不得传入「网盘最近文件」列表冒充附件
     */
    public static List<Map<String, String>> apply(
            List<String> attachedIds,
            List<DataFile> attachedFiles,
            List<Map<String, String>> messages,
            QianxunProperties properties
    ) {
        if (attachedIds == null || attachedIds.isEmpty()) {
            return messages;
        }
        LinkedHashSet<String> want = new LinkedHashSet<>(attachedIds);
        List<DataFile> ordered = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (attachedFiles != null) {
            for (DataFile f : attachedFiles) {
                if (f == null || f.isFolder() || !want.contains(f.id()) || seen.contains(f.id())) {
                    continue;
                }
                ordered.add(f);
                seen.add(f.id());
            }
        }
        if (ordered.isEmpty()) {
            return messages;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户本轮在聊天中附上了文档与图片（Word/Excel/PDF/图片等）。下面给出抽出的正文或图片说明，请直接依据这些材料回答。");
        sb.append("图片请通过公开链接阅读画面中的文字、人物、物体与场景；文档若摘录不足可再下载原文件。\n");
        sb.append("给用户看的下载地址请用「用户链接」（相对路径），不要把 Docker 内部主机名发给用户。\n");
        int n = 0;
        int budget = 80_000;
        for (DataFile f : ordered) {
            String url = FilePublicLinks.url(properties, f.publicToken());
            boolean image = DataFile.KIND_IMAGE.equals(f.kind());
            int limit = 24_000;
            sb.append("\n### 【本轮聊天附件】 ");
            sb.append(f.name());
            if (!FolderPaths.normalize(f.folderPath()).isEmpty()) {
                sb.append("\n目录：").append(f.folderPath());
            }
            if (image) {
                sb.append("\n类型：图片");
            }
            if (!url.isBlank()) {
                sb.append("\n公开链接：").append(url);
                String relative = FilePublicLinks.relativePath(f.publicToken());
                if (!relative.isBlank()) {
                    sb.append("\n用户链接：").append(relative);
                }
                if (image) {
                    sb.append("\n![").append(f.name()).append("](").append(url).append(")");
                    sb.append("\n请使用视觉能力阅读该图片。");
                }
            }
            String body = f.detailText();
            if (body != null && !body.isBlank() && !body.startsWith("已上传文档") && !body.startsWith("已上传图片")) {
                if (body.length() > limit) {
                    body = body.substring(0, limit) + "\n…（摘录已截断）";
                }
                sb.append(image ? "\n说明：\n" : "\n正文：\n").append(body).append('\n');
            } else if (!image) {
                sb.append("\n（未能抽出正文，请下载原文件阅读）\n");
            }
            n++;
            if (sb.length() >= budget) {
                sb.append("\n…（其余文档已省略）\n");
                break;
            }
        }
        if (n == 0) {
            return messages;
        }
        List<Map<String, String>> out = new ArrayList<>(messages.size() + 1);
        out.add(Map.of("role", "system", "content", sb.toString()));
        out.addAll(messages);
        return out;
    }
}
