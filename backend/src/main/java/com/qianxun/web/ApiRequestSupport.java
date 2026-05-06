package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.GeneralArgument;

public final class ApiRequestSupport {

    private ApiRequestSupport() {}

    public static <T> T jsonArg(ApiRequest<T> request) {
        return request == null ? null : request.jsonArg();
    }

    public static void applyGeneralArgument(ApiRequest<?> request) {
        if (request == null || request.generalArgument() == null) {
            return;
        }
        GeneralArgument arg = request.generalArgument();
        UserContext.set(arg.userId(), arg.loginName(), arg.loginName());
    }
}
