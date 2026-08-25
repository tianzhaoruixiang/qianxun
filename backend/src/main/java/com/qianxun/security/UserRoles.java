package com.qianxun.security;

/**
 * 账号角色：管理员可创建功能用户，并可新建/修改/删除智能体；
 * 功能用户可使用产品（浏览、对话）但不能建号或管理智能体。
 */
public final class UserRoles {

    public static final String ADMIN = "admin";
    public static final String FUNCTIONAL = "functional";

    private UserRoles() {}

    public static boolean isAdmin(String role) {
        return ADMIN.equalsIgnoreCase(role == null ? "" : role.trim());
    }

    public static String normalize(String role) {
        return isAdmin(role) ? ADMIN : FUNCTIONAL;
    }
}
