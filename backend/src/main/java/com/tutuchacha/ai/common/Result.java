package com.tutuchacha.ai.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

/**
 * 统一响应体：{ code, message, data, traceId }。
 * 成功 code = 0；失败 code 与 HTTP 状态码一致。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Result<T>(int code, String message, T data, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, currentTraceId());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null, currentTraceId());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null, currentTraceId());
    }

    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }
}
