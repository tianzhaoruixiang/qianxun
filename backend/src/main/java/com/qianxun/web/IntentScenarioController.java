package com.qianxun.web;

import com.qianxun.service.QianXunServiceIntentScenario;
import com.qianxun.web.dto.IntentScenarioResponse;
import com.qianxun.web.dto.UpsertIntentScenarioRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/QianXunService/intent-scenarios")
public class IntentScenarioController {

    private final QianXunServiceIntentScenario service;

    public IntentScenarioController(QianXunServiceIntentScenario service) {
        this.service = service;
    }

    @GetMapping
    public List<IntentScenarioResponse> list(
            @RequestParam(value = "enabledOnly", defaultValue = "false") boolean enabledOnly
    ) {
        var data = enabledOnly ? service.listEnabled() : service.listAll();
        return data.stream().map(IntentScenarioResponse::from).toList();
    }

    @GetMapping("/{id}")
    public IntentScenarioResponse get(@PathVariable("id") String id) {
        return IntentScenarioResponse.from(service.get(id));
    }

    @PostMapping
    public IntentScenarioResponse create(@RequestBody UpsertIntentScenarioRequest req) {
        return IntentScenarioResponse.from(service.create(req));
    }

    @PatchMapping("/{id}")
    public IntentScenarioResponse update(
            @PathVariable("id") String id,
            @RequestBody UpsertIntentScenarioRequest req
    ) {
        return IntentScenarioResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
