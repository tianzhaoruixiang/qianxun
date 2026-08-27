package com.qianxun.web.dto;

/**
 * 会话列表分页。page 从 1 起；也可直接传 offset。
 * 同时传入 offset 与 page 时以 offset 为准。
 * 传入 cursorUpdatedAt + cursorId 时走游标分页（适合万级会话，避免大 OFFSET）。
 */
public record ListSessionsRequest(
        Integer page,
        Integer limit,
        Integer offset,
        String keyword,
        String agentGroup,
        String cursorUpdatedAt,
        String cursorId
) {
    public ListSessionsRequest(Integer page, Integer limit, Integer offset) {
        this(page, limit, offset, null, null, null, null);
    }
}
