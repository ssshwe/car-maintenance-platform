package com.example.maintenance.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final int status;
    public BusinessException(String code, String message, int status) { super(message); this.code = code; this.status = status; }
    public BusinessException(String code, String message) { this(code, message, 409); }
    public static void require(boolean condition, String code, String message) {
        if (!condition) throw new BusinessException(code, message);
    }
}
