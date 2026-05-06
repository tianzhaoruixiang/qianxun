package com.qianxun.web.dto;

import java.util.List;

public record DataPortraitResponse(
        String unit,
        List<String> labels,
        List<Integer> seriesA,
        List<Integer> seriesB,
        Integer focusIndex,
        String focusLabel
) {}
