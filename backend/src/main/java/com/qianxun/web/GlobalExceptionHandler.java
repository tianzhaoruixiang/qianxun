package com.qianxun.web;

import com.qianxun.web.dto.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String reason = ex.getReason();
        String message = reason == null || reason.isBlank()
                ? HttpStatus.valueOf(code).getReasonPhrase()
                : reason;
        return ResponseEntity.status(code).body(ApiResponse.error(code, message));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataAccess(DataAccessException ex) {
        String message = "数据库暂时不可用，请稍后重试";
        String root = rootCauseMessage(ex);
        if (containsIgnoreCase(root, "MEM_ALLOC_FAILED") || containsIgnoreCase(root, "Allocator sys memory check failed")) {
            message = "数据库内存资源紧张，请稍后重试";
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE.value(), message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(413, "文件过大，超过上传限制"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "系统异常"
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), message));
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }

    private static boolean containsIgnoreCase(String source, String marker) {
        return source != null && marker != null && source.toLowerCase().contains(marker.toLowerCase());
    }
}
