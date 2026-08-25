package com.qianxun.web.dto;

public record UpdateSessionApiRequest(
        String id,
        String title,
        SessionGoalRequest goal,
        Boolean clearGoal
) {}
