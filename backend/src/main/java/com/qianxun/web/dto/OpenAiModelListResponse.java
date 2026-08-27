package com.qianxun.web.dto;

import java.util.List;

public record OpenAiModelListResponse(List<String> models, List<OpenAiModelItemResponse> items) {}
