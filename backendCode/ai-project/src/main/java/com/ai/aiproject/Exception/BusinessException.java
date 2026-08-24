package com.ai.aiproject.Exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final String message;
    private final Object data;

    public BusinessException(String message) {
        super(message);
        this.code = "BUSINESS_ERROR";
        this.message = message;
        this.data = null;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.data = null;
    }
}
