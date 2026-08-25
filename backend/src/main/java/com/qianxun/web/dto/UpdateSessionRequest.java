package com.qianxun.web.dto;

public record UpdateSessionRequest(
        String title,
        SessionGoalRequest goal,
        Boolean clearGoal
) {
    public UpdateSessionRequest(String title) {
        this(title, null, null);
    }
}
