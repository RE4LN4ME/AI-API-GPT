package com.chattingapi.chatbot.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class UpstreamException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode errorCode;

    public UpstreamException(String message) {
        super(message);
        this.status = HttpStatus.BAD_GATEWAY;
        this.errorCode = ErrorCode.UPSTREAM_ERROR;
    }

    public UpstreamException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
