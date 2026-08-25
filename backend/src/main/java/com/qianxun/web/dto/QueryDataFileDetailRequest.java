package com.qianxun.web.dto;

public record QueryDataFileDetailRequest(
        String id,
        String publicToken
) {
    public QueryDataFileDetailRequest(String id) {
        this(id, null);
    }
}

