package com.qianxun.web.dto;

public record CreateFolderRequest(
        String name,
        String parentPath
) {}
