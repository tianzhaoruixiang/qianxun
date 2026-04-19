package com.qianxun.web.dto;

import java.time.Instant;

public record ChatSessionResponse(String id, String title, Instant createdAt, Instant updatedAt) {
}
