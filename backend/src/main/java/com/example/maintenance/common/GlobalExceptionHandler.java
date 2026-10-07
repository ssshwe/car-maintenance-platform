package com.example.maintenance.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private ResponseEntity<ApiResult<Object>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiResult<>(code, message, null, MDC.get("traceId")));
    }
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResult<Object>> business(BusinessException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResult<Object>> validation(MethodArgumentNotValidException e) {
        return error(400, "VALIDATION_ERROR", e.getBindingResult().getFieldErrors().stream().map(x -> x.getDefaultMessage()).distinct().reduce((a,b) -> a + "；" + b).orElse("输入不合法"));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, ConstraintViolationException.class, java.time.format.DateTimeParseException.class})
    ResponseEntity<ApiResult<Object>> invalid(Exception e) { return error(400, "VALIDATION_ERROR", "输入格式或参数不正确"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResult<Object>> duplicate(DataIntegrityViolationException e) { return error(409, "DATA_CONFLICT", "账号、手机号、商品编码或业务记录重复，请刷新后重试"); }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ApiResult<Object>> notFound(Exception e) { return error(404,"NOT_FOUND","页面或资源不存在"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResult<Object>> unknown(Exception e) {
        log.error("请求处理失败 traceId={}", MDC.get("traceId"), e);
        return error(500, "SYSTEM_ERROR", "服务暂时无法完成操作，请稍后重试");
    }
}
