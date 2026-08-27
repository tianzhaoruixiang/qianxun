package com.qianxun.web;

import com.qianxun.service.QianXunServiceWelcome;
import com.qianxun.service.SystemSettingsService;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.ListOpenAiModelsRequest;
import com.qianxun.web.dto.OpenAiModelListResponse;
import com.qianxun.web.dto.SystemSettingsResponse;
import com.qianxun.web.dto.UpdateSystemSettingsRequest;
import com.qianxun.web.dto.UpdateWelcomePresetsRequest;
import com.qianxun.web.dto.WelcomeBootstrapResponse;
import com.qianxun.web.dto.WelcomePresetsResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/QianXunService/welcome")
public class WelcomeController {

    private final QianXunServiceWelcome welcomeService;
    private final SystemSettingsService systemSettingsService;

    public WelcomeController(QianXunServiceWelcome welcomeService, SystemSettingsService systemSettingsService) {
        this.welcomeService = welcomeService;
        this.systemSettingsService = systemSettingsService;
    }

    /** 聚合首页文案、推荐问题、工具中文名、画像图例等（前端初始化调用一次即可） */
    @PostMapping("/bootstrap")
    public ApiResponse<WelcomeBootstrapResponse> bootstrap(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(welcomeService.bootstrap());
    }

    /** 登录页可用：不要求鉴权，只返回系统名称。 */
    @PostMapping("/brand")
    public ApiResponse<SystemSettingsResponse> brand(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(SystemSettingsResponse.brandOnly(systemSettingsService.resolvedSystemName()));
    }

    @PostMapping("/system")
    public ApiResponse<SystemSettingsResponse> systemSettings(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(systemSettingsService.snapshot());
    }

    @PostMapping("/system/models")
    public ApiResponse<OpenAiModelListResponse> listUpstreamModels(
            @RequestBody(required = false) ApiRequest<ListOpenAiModelsRequest> request
    ) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(systemSettingsService.listUpstreamModels(ApiRequestSupport.jsonArg(request)));
    }

    @PostMapping("/system/update")
    public ApiResponse<SystemSettingsResponse> updateSystem(
            @RequestBody(required = false) ApiRequest<UpdateSystemSettingsRequest> request
    ) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(systemSettingsService.update(ApiRequestSupport.jsonArg(request)));
    }

    @PostMapping("/presets")
    public ApiResponse<WelcomePresetsResponse> presets(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(welcomeService.loadPresets());
    }

    @PostMapping("/presets/update")
    public ApiResponse<WelcomePresetsResponse> updatePresets(
            @RequestBody(required = false) ApiRequest<UpdateWelcomePresetsRequest> request
    ) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(welcomeService.updatePresets(ApiRequestSupport.jsonArg(request)));
    }
}
