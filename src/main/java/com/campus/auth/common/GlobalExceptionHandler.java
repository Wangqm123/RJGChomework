package com.campus.auth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：任何接口抛出的异常都统一转成
 * {code, msg, data} 格式，不让堆栈直接暴露给前端。
 *
 * 对应需求文档非功能性需求「可靠性」：跨服务调用失败给出友好提示，不直接崩溃。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按异常里带的业务码返回 */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /** 缺少必填参数：400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResponse.fail(400, "缺少必填参数：" + e.getParameterName());
    }

    /** 请求体字段校验不通过（@NotNull/@Max 等）：400，提示第一个字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("参数校验不通过");
        return ApiResponse.fail(400, msg);
    }

    /** 兜底异常：500 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("服务内部异常", e);
        return ApiResponse.fail(500, "服务内部异常，请稍后重试");
    }
}
