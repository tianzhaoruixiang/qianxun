package com.qianxun.web;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.AppUser;
import com.qianxun.security.JwtService;
import com.qianxun.security.UserRoles;
import com.qianxun.service.QianXunServiceUser;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.LoginRequest;
import com.qianxun.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号密码登录，签发 JWT。
 */
@RestController
@RequestMapping("/QianXunService/auth")
public class AuthController {

    private final QianxunProperties properties;
    private final JwtService jwtService;
    private final QianXunServiceUser userService;

    public AuthController(QianxunProperties properties, JwtService jwtService, QianXunServiceUser userService) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("ok");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody @Valid LoginRequest request) {
        AppUser user = userService.authenticate(request.username(), request.password()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "用户名或密码错误"));
        }
        String role = UserRoles.normalize(user.role());
        long exp = Math.max(60, properties.getAuth().getJwtExpirationSeconds());
        String token = jwtService.createToken(user.id(), user.username(), user.displayName(), role);
        LoginResponse body = new LoginResponse(
                token,
                exp,
                user.id(),
                user.username(),
                user.displayName(),
                role
        );
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
