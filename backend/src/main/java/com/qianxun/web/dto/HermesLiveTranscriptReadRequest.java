package com.qianxun.web.dto;

public record HermesLiveTranscriptReadRequest(
        String profile,
        String delegationId,
        Integer taskIndex,
        Integer maxChars
) {
}
