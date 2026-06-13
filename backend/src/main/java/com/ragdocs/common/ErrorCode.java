package com.ragdocs.common;

public enum ErrorCode {
    PARAM_ERROR(40001, "参数错误"),
    UNAUTHENTICATED(40101, "未认证"),
    FORBIDDEN(40301, "非本人资源"),
    NOT_FOUND(40401, "不存在"),
    CONFLICT(40901, "重复"),
    UNSUPPORTED_FILE(42201, "文件类型不支持或超限"),
    INTERNAL_ERROR(50001, "内部错误"),
    LLM_CALL_FAILED(50201, "LLM 调用失败"),
    EMBEDDING_CALL_FAILED(50202, "Embedding 调用失败");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
