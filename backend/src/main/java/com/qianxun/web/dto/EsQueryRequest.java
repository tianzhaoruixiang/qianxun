package com.qianxun.web.dto;

import java.util.List;
import java.util.Map;

public record EsQueryRequest(
        String index,
        Map<String, Object> query,
        Map<String, Object> _source,
        Integer from,
        Integer size
) { }