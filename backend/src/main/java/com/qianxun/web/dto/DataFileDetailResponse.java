package com.qianxun.web.dto;

import java.util.List;

public record DataFileDetailResponse(
        String id,
        String name,
        String date,
        String kind,
        String detailText,
        List<List<String>> excelRows
) {}

