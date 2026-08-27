package com.qianxun.web.dto;

import java.util.List;

public record ChatSessionListResponse(
        List<ChatSessionResponse> items,
        int page,
        int limit,
        int offset,
        boolean hasMore,
        List<SessionAgentFacet> agentFacets
) {
    public ChatSessionListResponse(
            List<ChatSessionResponse> items,
            int page,
            int limit,
            int offset,
            boolean hasMore
    ) {
        this(items, page, limit, offset, hasMore, List.of());
    }
}
