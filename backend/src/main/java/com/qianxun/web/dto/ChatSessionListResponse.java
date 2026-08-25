package com.qianxun.web.dto;

import java.util.List;

public record ChatSessionListResponse(
        List<ChatSessionResponse> items,
        int page,
        int limit,
        int offset,
        boolean hasMore
) {}
