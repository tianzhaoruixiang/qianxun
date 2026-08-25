package com.qianxun.web.dto;

public record UpsertDataFileRequest(
        String id,
        String name,
        String date,
        String kind,
        String detailText,
        String detailJson,
        String folderPath
) {}

