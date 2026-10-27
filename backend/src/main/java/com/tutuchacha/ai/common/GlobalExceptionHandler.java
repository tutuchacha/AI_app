package com.tutuchacha.ai.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 所有异常出口：统一返回 { code, message, data, traceId }，HTTP 状态与 code 对齐。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        return build(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return build(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return build(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception ex) {
        return build(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "接口不存在: /" + ex.getResourcePath());
    }

    /** 超过 multipart 上限：容器在进入 controller 前就抛，统一成 400 便于前端提示。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.BAD_REQUEST, "文件超过大小限制");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("未处理异常 traceId={}", MDC.get(TraceIdFilter.MDC_KEY), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<Result<Void>> build(ErrorCode errorCode, String message) {
        HttpStatus status = HttpStatus.resolve(errorCode.getCode());
        HttpStatus resolved = status == null ? HttpStatus.BAD_REQUEST : status;
        return ResponseEntity.status(resolved).body(Result.fail(errorCode, message));
    }
}
