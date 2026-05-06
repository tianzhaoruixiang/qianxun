package com.qianxun.web;

import com.qianxun.service.QianXunServiceUser;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.UserResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户信息接口（暂不含登录，仅返回当前用户信息）
 * POST /QianXunService/users/me
 */
@RestController
@RequestMapping("/QianXunService/users")
public class UserController {

    private final QianXunServiceUser userService;

    public UserController(QianXunServiceUser userService) {
        this.userService = userService;
    }

    @PostMapping("/me")
    public ApiResponse<UserResponse> me(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(userService.getCurrentUser());
    }
}
