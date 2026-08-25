package com.qianxun.web.dto;

public record HermesLiveTranscriptContentResponse(
        boolean ok,
        String delegationId,
        Integer taskIndex,
        String path,
        String content,
        String message
) {
}
