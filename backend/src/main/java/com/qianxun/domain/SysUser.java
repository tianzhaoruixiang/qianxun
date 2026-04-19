package com.qianxun.domain;

/**
 * 用户信息值对象。
 * 用户数据由外部系统通过请求头传入，本系统不存储，仅在内存中流转。
 */
public record SysUser(
        String id,
        String username,
        String displayName,
        String avatarUrl
) {}
