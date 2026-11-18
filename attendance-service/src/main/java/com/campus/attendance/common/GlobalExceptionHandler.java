package com.campus.attendance.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：任何接口抛出的异常都统一转成 {code, msg, data}，
 * 不让堆栈直接暴露给前端（需求文档非功能性需求「可靠性」）。
 *
 * 本服务的错误码、返回结构与权限服务/岗位服务保持完全一致，前端/联调方无感知。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按异常里带的业务码返回（400/401/403/500） */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /** 缺少必填参数：400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResponse.fail(400, "缺少必填参数：" + e.getParameterName());
    }

    /** 参数类型不对，例如 page=abc：400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResponse.fail(400, "参数格式错误：" + e.getName());
    }

    /** 请求体字段校验不通过（@NotNull 等）：400，提示第一个字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("参数校验不通过");
        return ApiResponse.fail(400, msg);
    }

    /** 请求体不是合法 JSON 或字段类型不匹配：400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return ApiResponse.fail(400, "请求体格式错误，请检查 JSON 字段类型");
    }

    /**
     * 数据库约束冲突：400。
     * 本服务主要出现在并发签到场景（同一岗位重复插入未签退记录），业务层已先查重，这里兜底。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据库约束冲突", e);
        return ApiResponse.fail(400, "数据已存在或违反约束，请勿重复提交");
    }

    /** 乐观锁冲突：400（保留与其他服务一致的兜底处理） */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ApiResponse<Void> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("并发修改冲突，已回滚本次操作：{}", e.getMessage());
        return ApiResponse.fail(400, "数据刚被其他人更新，请刷新后重试");
    }

    /** 兜底异常：500 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("服务内部异常", e);
        return ApiResponse.fail(500, "服务内部异常，请稍后重试");
    }
}
