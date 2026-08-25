package com.qianxun.web.dto;

import java.util.List;

public record HermesLiveDelegationResponse(
        String delegationId,
        String path,
        String started,
        String completed,
        int taskCount,
        List<HermesLiveTaskLogResponse> tasks
) {
}
