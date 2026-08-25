package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.repo.SuggestedQuestionRepository;
import com.qianxun.repo.UiConfigRepository;
import com.qianxun.web.dto.SuggestedQuestionResponse;
import com.qianxun.web.dto.UpdateWelcomePresetsRequest;
import com.qianxun.web.dto.WelcomeBootstrapResponse;
import com.qianxun.web.dto.WelcomePresetsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QianXunServiceWelcome {

    public static final String KEY_DISCLAIMER = "welcome.disclaimer";
    public static final String KEY_GREETING = "welcome.greeting";
    public static final String KEY_CAPABILITY = "welcome.capability";
    public static final String KEY_RECOMMEND_LABEL = "welcome.recommend_label";
    public static final String KEY_PORTRAIT_A = "portrait.series_a_label";
    public static final String KEY_PORTRAIT_B = "portrait.series_b_label";

    private final UiConfigRepository uiConfigRepository;
    private final SuggestedQuestionRepository suggestedQuestionRepository;
    private final ToolDisplayNames toolDisplayNames;

    public QianXunServiceWelcome(
            UiConfigRepository uiConfigRepository,
            SuggestedQuestionRepository suggestedQuestionRepository,
            ToolDisplayNames toolDisplayNames
    ) {
        this.uiConfigRepository = uiConfigRepository;
        this.suggestedQuestionRepository = suggestedQuestionRepository;
        this.toolDisplayNames = toolDisplayNames;
    }

    public WelcomeBootstrapResponse bootstrap() {
        String disclaimer = uiConfigRepository.findValue(KEY_DISCLAIMER).orElse("");
        String greeting = uiConfigRepository.findValue(KEY_GREETING).orElse("");
        String capability = uiConfigRepository.findValue(KEY_CAPABILITY).orElse("");
        String recommendLabel = uiConfigRepository.findValue(KEY_RECOMMEND_LABEL).orElse("");
        String portraitA = uiConfigRepository.findValue(KEY_PORTRAIT_A).orElse("");
        String portraitB = uiConfigRepository.findValue(KEY_PORTRAIT_B).orElse("");

        List<SuggestedQuestionResponse> questions = suggestedQuestionRepository.listEnabledOrderBySort().stream()
                .map(q -> new SuggestedQuestionResponse(q.id(), q.text(), q.category()))
                .toList();

        Map<String, String> tools = new LinkedHashMap<>(toolDisplayNames.allDisplayNames());
        String[] presets = resolvedPresets(questions.stream().map(SuggestedQuestionResponse::text).toList());

        return new WelcomeBootstrapResponse(
                disclaimer, greeting, capability, recommendLabel,
                portraitA, portraitB,
                questions,
                tools,
                presets[0], presets[1], presets[2]
        );
    }

    /** 管理端表单：已保存则原样返回；从未配置时回填当前欢迎页实际展示的三条。 */
    public WelcomePresetsResponse loadPresets() {
        String s1 = uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_1);
        String s2 = uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_2);
        String s3 = uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_3);
        if (WelcomeOfficerPresets.clip(s1).isEmpty()
                && WelcomeOfficerPresets.clip(s2).isEmpty()
                && WelcomeOfficerPresets.clip(s3).isEmpty()) {
            String[] resolved = resolvedPresets(suggestedTexts());
            return new WelcomePresetsResponse(resolved[0], resolved[1], resolved[2]);
        }
        return new WelcomePresetsResponse(
                WelcomeOfficerPresets.clip(s1),
                WelcomeOfficerPresets.clip(s2),
                WelcomeOfficerPresets.clip(s3)
        );
    }

    public WelcomePresetsResponse updatePresets(UpdateWelcomePresetsRequest body) {
        if (!UserContext.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可修改数智干警预置对话");
        }
        String p1 = WelcomeOfficerPresets.clip(body == null ? null : body.presetChat1());
        String p2 = WelcomeOfficerPresets.clip(body == null ? null : body.presetChat2());
        String p3 = WelcomeOfficerPresets.clip(body == null ? null : body.presetChat3());
        uiConfigRepository.upsert(WelcomeOfficerPresets.KEY_1, p1);
        uiConfigRepository.upsert(WelcomeOfficerPresets.KEY_2, p2);
        uiConfigRepository.upsert(WelcomeOfficerPresets.KEY_3, p3);
        return new WelcomePresetsResponse(p1, p2, p3);
    }

    private String[] resolvedPresets(List<String> suggested) {
        return WelcomeOfficerPresets.resolve(
                uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_1),
                uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_2),
                uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_3),
                suggested
        );
    }

    private List<String> suggestedTexts() {
        return suggestedQuestionRepository.listEnabledOrderBySort().stream()
                .map(q -> q.text() == null ? "" : q.text())
                .toList();
    }
}
