package com.tutuchacha.ai.common;

/**
 * 统一错误码：code 与 HTTP 状态码保持一致，成功为 0。
 * 前端只判断 code === 0，其余按 code 做提示或跳转。
 */
public enum ErrorCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据冲突"),
    INTERNAL_ERROR(500, "服务器开小差了"),
    BAD_GATEWAY(502, "上游模型调用失败"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
