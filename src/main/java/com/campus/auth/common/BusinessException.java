package com.campus.auth.common;

/**
 * 业务异常：携带接口文档约定的业务错误码（400/401/403/500），
 * 由 GlobalExceptionHandler 统一捕获并转成 ApiResponse。
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
