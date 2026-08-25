package com.qianxun.web.dto;

public record DataFileResponse(
        String id,
        String name,
        String date,
        String kind,
        String publicUrl,
        String publicToken,
        String contentType,
        Long sizeBytes,
        String preview,
        String folderPath
) {
    public DataFileResponse(String id, String name, String date, String kind) {
        this(id, name, date, kind, null, null, null, null, null, "");
    }
}
