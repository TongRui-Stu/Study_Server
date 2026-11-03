package com.tongrui.study.client;

import com.tongrui.study.common.exception.BusinessException;

public class AiProviderException extends BusinessException {

    public enum Category {
        NOT_CONFIGURED,
        CONNECTION,
        TIMEOUT,
        RATE_LIMIT,
        UPSTREAM_SERVER,
        BAD_REQUEST,
        AUTHENTICATION,
        QUOTA,
        NOT_FOUND,
        INVALID_RESPONSE,
        EMPTY_RESPONSE,
        REJECTED
    }

    private final Category category;
    private final boolean failoverAllowed;

    public AiProviderException(int code, String message, Category category, boolean failoverAllowed) {
        super(code, message);
        this.category = category;
        this.failoverAllowed = failoverAllowed;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isFailoverAllowed() {
        return failoverAllowed;
    }
}
