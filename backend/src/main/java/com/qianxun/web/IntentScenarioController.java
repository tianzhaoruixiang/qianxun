package com.qianxun.web;

import com.qianxun.service.QianXunServiceIntentScenario;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.IdRequest;
import com.qianxun.web.dto.IntentScenarioResponse;
import com.qianxun.web.dto.ListIntentScenarioRequest;
import com.qianxun.web.dto.UpdateIntentScenarioApiRequest;
import com.qianxun.web.dto.UpsertIntentScenarioRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/QianXunService/intent-scenarios")
public class IntentScenarioController {

    private final QianXunServiceIntentScenario service;

    public IntentScenarioController(QianXunServiceIntentScenario service) {
        this.service = service;
    }

    @PostMapping("/list")
    public ApiResponse<List<IntentScenarioResponse>> list(
            @RequestBody(required = false) ApiRequest<ListIntentScenarioRequest> request
    ) {
        ApiRequestSupport.applyGeneralArgument(request);
        ListIntentScenarioRequest arg = ApiRequestSupport.jsonArg(request);
        boolean enabledOnly = arg != null && arg.enabledOnlyValue();
        var data = enabledOnly ? service.listEnabled() : service.listAll();
        return ApiResponse.success(data.stream().map(IntentScenarioResponse::from).toList());
    }

    @PostMapping("/get")
    public ApiResponse<IntentScenarioResponse> get(@RequestBody ApiRequest<IdRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(IntentScenarioResponse.from(service.get(ApiRequestSupport.jsonArg(request).id())));
    }

    @PostMapping("/create")
    public ApiResponse<IntentScenarioResponse> create(@RequestBody ApiRequest<UpsertIntentScenarioRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        return ApiResponse.success(IntentScenarioResponse.from(service.create(ApiRequestSupport.jsonArg(request))));
    }

    @PostMapping("/update")
    public ApiResponse<IntentScenarioResponse> update(@RequestBody ApiRequest<UpdateIntentScenarioApiRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpdateIntentScenarioApiRequest arg = ApiRequestSupport.jsonArg(request);
        return ApiResponse.success(IntentScenarioResponse.from(service.update(arg.id(), arg.scenario())));
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody ApiRequest<IdRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        service.delete(ApiRequestSupport.jsonArg(request).id());
        return ApiResponse.success(null);
    }
}
