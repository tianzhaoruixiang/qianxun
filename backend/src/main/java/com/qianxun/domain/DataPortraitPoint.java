package com.qianxun.domain;

public record DataPortraitPoint(
        String id,
        String groupCode,
        String unit,
        int orderIndex,
        String label,
        int seriesA,
        int seriesB,
        boolean focused,
        String focusLabel
) {
    public static final String DEFAULT_GROUP = "default";
}
