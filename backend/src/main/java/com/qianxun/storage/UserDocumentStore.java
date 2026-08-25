package com.qianxun.storage;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.DataFile;
import com.qianxun.repo.DataFileRepository;
import com.qianxun.web.dto.DataFileResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 把字节写入当前用户云盘（MinIO + data_file），供上传与 Hermes 生成文档入库共用。
 */
@Component
public class UserDocumentStore {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DataFileRepository fileRepository;
    private final MinioStorage minioStorage;
    private final QianxunProperties properties;

    public UserDocumentStore(
            DataFileRepository fileRepository,
            MinioStorage minioStorage,
            QianxunProperties properties
    ) {
        this.fileRepository = fileRepository;
        this.minioStorage = minioStorage;
        this.properties = properties;
    }

    public DataFileResponse persistBytes(
            String userId,
            String filename,
            byte[] bytes,
            String contentType,
            String folderPath
    ) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        if (!minioStorage.isEnabled()) {
            throw new IllegalStateException("对象存储未配置");
        }
        String id = newId();
        String token = newId();
        String kind = DataFile.kindFromFilename(filename);
        String type = (contentType == null || contentType.isBlank())
                ? guessContentType(filename)
                : contentType;
        OfficeContentExtractor.Result extracted = OfficeContentExtractor.extract(filename, bytes);
        String detailText = extracted.hasText()
                ? extracted.text()
                : buildUploadNote(filename, bytes.length, kind);
        String detailJson = extracted.excelJson();
        Instant now = Instant.now();
        String objectKey;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            objectKey = minioStorage.putUserFile(userId, id, filename, in, bytes.length, type);
        } catch (Exception ex) {
            throw new IllegalStateException("上传到对象存储失败", ex);
        }
        String folder = ensureFolderChain(userId, FolderPaths.normalize(folderPath));
        DataFile row = new DataFile(
                id, userId, filename, DATE_FMT.format(now.atZone(ZoneId.systemDefault())),
                kind, detailText, detailJson, objectKey, type, (long) bytes.length, token, folder, now, now
        );
        fileRepository.insert(row);
        return toResponse(row);
    }

    public String ensureFolderChain(String userId, String path) {
        String normalized = FolderPaths.normalize(path);
        if (normalized.isEmpty()) {
            return "";
        }
        String parent = "";
        Instant now = Instant.now();
        String date = DATE_FMT.format(now.atZone(ZoneId.systemDefault()));
        for (String seg : normalized.split("/")) {
            if (fileRepository.findFolder(userId, parent, seg).isEmpty()) {
                DataFile folder = new DataFile(
                        newId(), userId, seg, date, DataFile.KIND_FOLDER,
                        null, null, null, null, null, null, parent, now, now
                );
                fileRepository.insert(folder);
            }
            parent = FolderPaths.join(parent, seg);
        }
        return normalized;
    }

    public DataFileResponse toResponse(DataFile f) {
        return new DataFileResponse(
                f.id(), f.name(), f.displayDate(), f.kind(),
                FilePublicLinks.url(properties, f.publicToken()),
                f.publicToken(), f.contentType(), f.sizeBytes(),
                previewOf(f.detailText()),
                FolderPaths.normalize(f.folderPath())
        );
    }

    public static String guessContentType(String filename) {
        String ext = extensionOf(filename);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "txt", "md" -> "text/plain; charset=utf-8";
            case "html", "htm" -> "text/html";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "zip" -> "application/zip";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
    }

    public static String extensionOf(String filename) {
        String name = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    private static String previewOf(String detailText) {
        if (detailText == null) {
            return null;
        }
        String t = detailText.strip().replace('\n', ' ').replace('\t', ' ');
        if (t.startsWith("已上传文档") || t.startsWith("已上传图片")) {
            return null;
        }
        if (t.length() > 120) {
            t = t.substring(0, 120) + "…";
        }
        return t.isBlank() ? null : t;
    }

    private static String buildUploadNote(String name, long size, String kind) {
        if (DataFile.KIND_IMAGE.equals(kind)) {
            return "已上传图片「" + name + "」（" + size + " 字节），可通过公开链接阅读画面。";
        }
        return "已上传文档「" + name + "」（" + size + " 字节），可通过公开链接阅读。";
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
