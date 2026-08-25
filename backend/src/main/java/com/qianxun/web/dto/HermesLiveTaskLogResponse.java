package com.qianxun.web.dto;

import java.util.List;

public record HermesLiveTaskLogResponse(
        int index,
        String path,
        String goal,
        String status,
        Long size
) {
}
