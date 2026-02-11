package com.chattingapi.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ApiError error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.of(true, data, null);
    }
    public static ApiResponse<Void> success() {
        return ApiResponse.of(true, null, null);
    }
    public static ApiResponse<Void> fail(String code, String message) {
        return ApiResponse.of(false, null, new ApiError(code, message));
    }

    @Getter @AllArgsConstructor
    public static class ApiError {
        private String code;
        private String message;
    }
}
