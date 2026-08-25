package com.qianxun.web.dto;

import java.util.List;

public record DataFileDetailResponse(
        String id,
        String name,
        String date,
        String kind,
        String detailText,
        List<List<String>> excelRows,
        String publicUrl,
        String publicToken,
        String contentType,
        Long sizeBytes,
        String folderPath
) {
    public DataFileDetailResponse(
            String id, String name, String date, String kind, String detailText, List<List<String>> excelRows
    ) {
        this(id, name, date, kind, detailText, excelRows, null, null, null, null, "");
    }
}
