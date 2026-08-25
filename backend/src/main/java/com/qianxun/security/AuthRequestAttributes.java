package com.qianxun.security;

/**
 * 标记当前请求已通过 JWT 建立身份，{@code UserContextInterceptor} 不得用客户端头覆盖。
 */
public final class AuthRequestAttributes {

    public static final String FROM_JWT = "com.qianxun.auth.fromJwt";

    private AuthRequestAttributes() {}
}
