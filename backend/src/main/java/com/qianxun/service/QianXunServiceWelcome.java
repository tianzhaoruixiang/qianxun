package com.qianxun.service;

import com.qianxun.domain.ToolDisplayName;
import com.qianxun.repo.SuggestedQuestionRepository;
import com.qianxun.repo.ToolDisplayNameRepository;
import com.qianxun.repo.UiConfigRepository;
import com.qianxun.web.dto.SuggestedQuestionResponse;
import com.qianxun.web.dto.WelcomeBootstrapResponse;
import org.springframework.stereotype.Service;

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
    private final ToolDisplayNameRepository toolDisplayNameRepository;

    public QianXunServiceWelcome(
            UiConfigRepository uiConfigRepository,
            SuggestedQuestionRepository suggestedQuestionRepository,
            ToolDisplayNameRepository toolDisplayNameRepository
    ) {
        this.uiConfigRepository = uiConfigRepository;
        this.suggestedQuestionRepository = suggestedQuestionRepository;
        this.toolDisplayNameRepository = toolDisplayNameRepository;
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

        Map<String, String> tools = new LinkedHashMap<>();
        for (ToolDisplayName row : toolDisplayNameRepository.listOrderBySort()) {
            tools.put(row.toolCode(), row.displayName());
        }

        return new WelcomeBootstrapResponse(
                disclaimer, greeting, capability, recommendLabel,
                portraitA, portraitB,
                questions,
                tools
        );
    }
}
