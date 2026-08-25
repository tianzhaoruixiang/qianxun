package com.qianxun.web.dto;

public record ListRunsRequest(
        Boolean runningOnly,
        Integer limit
) {}
