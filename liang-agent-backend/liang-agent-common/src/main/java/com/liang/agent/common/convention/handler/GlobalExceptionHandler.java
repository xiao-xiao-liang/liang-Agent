package com.liang.agent.common.convention.handler;

import com.liang.agent.common.convention.errorcode.BaseErrorCode;
import com.liang.agent.common.convention.exception.AbstractException;
import com.liang.agent.common.convention.result.Result;
import com.liang.agent.common.convention.result.Results;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 拦截指定异常并通过优雅构建方式返回前端信息。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截参数验证异常
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        // 替代 CollectionUtil.getFirst()：直接判断列表是否为空并取第一个元素
        String exceptionStr = fieldErrors.isEmpty()
                ? ""
                : Optional.ofNullable(fieldErrors.getFirst().getDefaultMessage()).orElse("");
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     * 拦截方法参数级校验异常（@Validated + @NotBlank 等）
     */
    @ExceptionHandler(value = ConstraintViolationException.class)
    public Result<Void> constraintViolationException(HttpServletRequest request, ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), message);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message);
    }

    /**
     * 拦截应用内抛出的异常
     */
    @ExceptionHandler(value = {AbstractException.class})
    public Result<Void> abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex, ex.getCause());
            return Results.failure(ex);
        }
        StringBuilder stackTraceBuilder = new StringBuilder();
        stackTraceBuilder.append(ex.getClass().getName()).append(": ").append(ex.getErrorMessage()).append("\n");
        StackTraceElement[] stackTrace = ex.getStackTrace();
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            stackTraceBuilder.append("\tat ").append(stackTrace[i]).append("\n");
        }
        log.error("[{}] {} [ex] {} \n\n{}", request.getMethod(), request.getRequestURL().toString(), ex, stackTraceBuilder);
        return Results.failure(ex);
    }

    /**
     * 拦截静态资源未找到异常（如 favicon.ico）
     */
    @ExceptionHandler(value = org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<Void> noResourceFoundExceptionHandler(HttpServletRequest request, org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("[{}] {} - {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), ex.getMessage());
    }

    /**
     * 拦截未捕获异常
     */
    @ExceptionHandler(value = Throwable.class)
    public Result<Void> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }

    /**
     * 拼接完整请求 URL（含查询参数）
     * <p>替代 StrUtil.isBlank()：使用 JDK 原生字符串判断</p>
     */
    private String getUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + queryString;
    }
}
