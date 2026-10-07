package com.example.maintenance.common;

import org.slf4j.MDC;

public record ApiResult<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResult<T> ok(T data) { return new ApiResult<>("OK", "操作成功", data, MDC.get("traceId")); }
}
