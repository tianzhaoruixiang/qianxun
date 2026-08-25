package com.qianxun.web.dto;

import java.util.List;

public record BatchUploadResponse(
        List<DataFileResponse> files,
        List<String> errors,
        int ok,
        int fail
) {}
