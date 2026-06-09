package com.teamec2.paymentsystem.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamec2.paymentsystem.global.exception.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int status,
        String code,
        String message,
        T data
) {

    private static final int BODY_STATUS = 200;
    private static final String DEFAULT_SUCCESS_MESSAGE = "요청이 성공했습니다.";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(BODY_STATUS, null, DEFAULT_SUCCESS_MESSAGE, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(BODY_STATUS, null, message, data);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(BODY_STATUS, errorCode.name(), errorCode.getMessage(), null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(BODY_STATUS, errorCode.name(), message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(BODY_STATUS, errorCode.name(), errorCode.getMessage(), data);
    }
}
