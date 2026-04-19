package com.qianxun.domain;

import java.time.Instant;

public record ChatSession(String id, String userId, String title, Instant createdAt, Instant updatedAt) {
}
