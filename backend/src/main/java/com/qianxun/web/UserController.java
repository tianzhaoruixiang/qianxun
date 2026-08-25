package com.qianxun.web;

import com.qianxun.service.QianXunServiceUser;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.CreateUserRequest;
import com.qianxun.web.dto.UserResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户信息；管理员可创建功能用户。
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

    @PostMapping("/create")
    public ApiResponse<UserResponse> create(@RequestBody(required = false) ApiRequest<CreateUserRequest> request) {
        return ApiResponse.success(userService.createFunctionalUser(ApiRequestSupport.jsonArg(request)));
    }
}
