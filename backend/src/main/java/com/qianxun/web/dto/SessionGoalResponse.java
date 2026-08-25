package com.qianxun.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qianxun.service.ChatGoalInvocation;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionGoalResponse(
        String title,
        String description,
        String steps,
        String constraints
) {
    public static SessionGoalResponse from(ChatGoalInvocation.Goal goal) {
        if (goal == null || goal.isBlank()) {
            return null;
        }
        return new SessionGoalResponse(
                blankToNull(goal.title()),
                blankToNull(goal.description()),
                blankToNull(goal.steps()),
                blankToNull(goal.constraints())
        );
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
