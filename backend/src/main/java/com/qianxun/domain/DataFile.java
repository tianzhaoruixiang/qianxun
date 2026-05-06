package com.qianxun.domain;

import java.time.Instant;

public record DataFile(
        String id,
        String name,
        String displayDate,
        String kind,
        String detailText,
        String detailJson,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String KIND_WORD  = "word";
    public static final String KIND_EXCEL = "excel";
}
