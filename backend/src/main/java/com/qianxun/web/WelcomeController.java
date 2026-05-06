package com.qianxun.web;

import com.qianxun.service.QianXunServiceWelcome;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.WelcomeBootstrapResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/QianXunService/welcome")
public class WelcomeController {

    private final QianXunServiceWelcome welcomeService;

    public WelcomeController(QianXunServiceWelcome welcomeService) {
        this.welcomeService = welcomeService;
    }

    /** 聚合首页文案、推荐问题、工具中文名、画像图例等（前端初始化调用一次即可） */
    @PostMapping("/bootstrap")
    public ApiResponse<WelcomeBootstrapResponse> bootstrap(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(welcomeService.bootstrap());
    }
}
