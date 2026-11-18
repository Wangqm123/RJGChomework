package com.campus.attendance.common;

/**
 * 统一返回格式（与权限服务、岗位服务完全一致，全组三个服务统一）：
 * {
 *   "code": 200,
 *   "msg": "操作成功",
 *   "data": {}
 * }
 *
 * code 取值：200 成功；401 未登录/Token 失效；403 权限不足；
 *           400 参数错误（含业务规则冲突）；500 服务内部异常。
 */
public class ApiResponse<T> {

    private int code;
    private String msg;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /** 成功（带数据） */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "操作成功", data);
    }

    /** 成功（无数据） */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(200, "操作成功", null);
    }

    /** 失败 */
    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
