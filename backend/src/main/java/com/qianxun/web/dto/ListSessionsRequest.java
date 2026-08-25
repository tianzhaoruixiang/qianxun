package com.qianxun.web.dto;

/**
 * 会话列表分页。page 从 1 起；也可直接传 offset。
 * 同时传入时以 offset 为准。
 */
public record ListSessionsRequest(
        Integer page,
        Integer limit,
        Integer offset
) {}
