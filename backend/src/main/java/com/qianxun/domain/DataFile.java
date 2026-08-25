package com.qianxun.domain;

import com.qianxun.storage.FolderPaths;

import java.time.Instant;
import java.util.Locale;

public record DataFile(
        String id,
        String userId,
        String name,
        String displayDate,
        String kind,
        String detailText,
        String detailJson,
        String objectKey,
        String contentType,
        Long sizeBytes,
        String publicToken,
        String folderPath,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String KIND_WORD  = "word";
    public static final String KIND_EXCEL = "excel";
    public static final String KIND_PDF   = "pdf";
    public static final String KIND_TEXT  = "text";
    public static final String KIND_IMAGE = "image";
    public static final String KIND_EML   = "eml";
    public static final String KIND_PPT   = "ppt";
    public static final String KIND_ARCHIVE = "archive";
    public static final String KIND_FILE  = "file";
    public static final String KIND_FOLDER = "folder";

    public static DataFile of(
            String id, String name, String displayDate, String kind,
            String detailText, String detailJson, Instant createdAt, Instant updatedAt
    ) {
        return new DataFile(id, "1", name, displayDate, kind, detailText, detailJson,
                null, null, null, null, "", createdAt, updatedAt);
    }

    public boolean isFolder() {
        return KIND_FOLDER.equals(kind);
    }

    public String fullPath() {
        if (isFolder()) {
            return FolderPaths.join(folderPath(), name());
        }
        return FolderPaths.normalize(folderPath());
    }

    public static String kindFromFilename(String filename) {
        String name = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (ext) {
            case "doc", "docx" -> KIND_WORD;
            case "xls", "xlsx", "csv" -> KIND_EXCEL;
            case "pdf" -> KIND_PDF;
            case "txt", "md", "json", "xml", "html", "htm" -> KIND_TEXT;
            case "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tif", "tiff" -> KIND_IMAGE;
            case "eml", "msg" -> KIND_EML;
            case "ppt", "pptx" -> KIND_PPT;
            case "zip", "rar", "7z", "tar", "gz", "tgz" -> KIND_ARCHIVE;
            default -> KIND_FILE;
        };
    }
}
