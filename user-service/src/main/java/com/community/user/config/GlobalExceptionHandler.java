package com.community.user.config;

import com.community.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理 —— 把 Java 异常转成前端能读的 Result
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数校验异常（@Validated 抛的） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleValidation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(msg);
    }

    /** 通用业务异常 */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntime(RuntimeException e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleAll(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
