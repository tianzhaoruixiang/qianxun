package com.qianxun.web;

import com.qianxun.service.QianXunUserService;
import com.qianxun.web.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户信息接口（暂不含登录，仅返回当前用户信息）
 * GET /api/users/me
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final QianXunUserService userService;

    public UserController(QianXunUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me() {
        return userService.getCurrentUser();
    }
}
