package com.qianxun.web.dto;

public record ApiRequest<T>(
        T jsonArg,
        GeneralArgument generalArgument
) {}
