package com.campus.attendance.common;

/**
 * 业务异常：携带接口文档约定的业务错误码（400/401/403/500），
 * 由 GlobalExceptionHandler 统一捕获并转成 ApiResponse。
 *
 * 本服务用它表达"业务规则冲突"，例如：
 * 未被录用却签到、重复签到、未签到就签退、跨部门查看汇总等。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
