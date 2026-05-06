package com.qianxun.web.dto;

import java.util.List;

public record UpsertDataPortraitRequest(
        String groupCode,
        String unit,
        List<PortraitPoint> points
) {
    public record PortraitPoint(
            String label,
            Integer seriesA,
            Integer seriesB,
            Boolean focused,
            String focusLabel
    ) {}
}

