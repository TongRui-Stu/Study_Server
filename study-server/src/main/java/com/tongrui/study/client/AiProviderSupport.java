package com.tongrui.study.client;

import org.springframework.util.StringUtils;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

final class AiProviderSupport {

    private AiProviderSupport() {
    }

    static AiChatResult parseResponse(Map<String, Object> response, String configuredModel, String provider) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw failure(502, "AI服务未返回有效内容，请稍后重试",
                    AiProviderException.Category.INVALID_RESPONSE, true);
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            throw failure(502, "AI服务响应格式异常，请稍后重试",
                    AiProviderException.Category.INVALID_RESPONSE, true);
        }
        String finishReason = valueAsString(choice.get("finish_reason"));
        if (finishReason.isEmpty()) {
            throw malformedResponse();
        }
        if (!finishReason.isEmpty() && !"stop".equals(finishReason) && !"length".equals(finishReason)) {
            throw failure(502, "AI回答未正常完成，请稍后重试",
                    AiProviderException.Category.REJECTED, false);
        }
        if (!(choice.get("message") instanceof Map<?, ?> message)) {
            throw failure(502, "AI服务响应格式异常，请稍后重试",
                    AiProviderException.Category.INVALID_RESPONSE, true);
        }
        String content = valueAsString(message.get("content"));
        if (!StringUtils.hasText(content)) {
            throw failure(502, "AI服务返回了空内容，请稍后重试",
                    AiProviderException.Category.EMPTY_RESPONSE, true);
        }
        if ("length".equals(finishReason)) {
            content = content.trim() + "\n\n> 回答因长度限制已截断，你可以继续追问。";
        } else if (!"stop".equals(finishReason)) {
            throw failure(502, "AI回答未正常完成，请稍后重试",
                    AiProviderException.Category.REJECTED, false);
        }
        String responseModel = valueAsString(response.get("model"));
        return new AiChatResult(content.trim(),
                StringUtils.hasText(responseModel) ? responseModel : configuredModel, provider);
    }

    static AiProviderException mapHttpError(int status) {
        return switch (status) {
            case 400, 422 -> failure(502, "AI服务请求参数异常，请稍后重试",
                    AiProviderException.Category.BAD_REQUEST, false);
            case 401, 403 -> failure(503, "AI服务认证失败，请联系管理员检查配置",
                    AiProviderException.Category.AUTHENTICATION, false);
            case 402 -> failure(503, "AI服务额度不足，请联系管理员",
                    AiProviderException.Category.QUOTA, false);
            case 404 -> failure(502, "AI服务地址或模型配置异常，请联系管理员",
                    AiProviderException.Category.NOT_FOUND, false);
            case 429 -> failure(503, "AI服务请求过于频繁，请稍后重试",
                    AiProviderException.Category.RATE_LIMIT, true);
            default -> {
                if (status >= 500) {
                    yield failure(503, "AI上游服务暂时不可用，请稍后重试",
                            AiProviderException.Category.UPSTREAM_SERVER, true);
                }
                yield failure(502, "AI服务请求失败，请稍后重试",
                        AiProviderException.Category.BAD_REQUEST, false);
            }
        };
    }

    static AiProviderException mapConnectionError(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException) {
                return failure(504, "连接AI服务超时，请稍后重试",
                        AiProviderException.Category.TIMEOUT, true);
            }
            if (cause instanceof HttpTimeoutException) {
                return failure(504, "等待AI回答超时，请稍后重试",
                        AiProviderException.Category.TIMEOUT, true);
            }
            cause = cause.getCause();
        }
        return failure(503, "无法连接AI服务，请稍后重试",
                AiProviderException.Category.CONNECTION, true);
    }

    static AiProviderException notConfigured() {
        return failure(503, "AI服务未配置，请联系管理员",
                AiProviderException.Category.NOT_CONFIGURED, true);
    }

    static AiProviderException malformedResponse() {
        return failure(502, "AI服务响应格式异常，请稍后重试",
                AiProviderException.Category.INVALID_RESPONSE, true);
    }

    static AiProviderException emptyResponse() {
        return failure(502, "AI服务返回了空内容，请稍后重试",
                AiProviderException.Category.EMPTY_RESPONSE, true);
    }

    static AiProviderException rejectedResponse() {
        return failure(502, "AI回答未正常完成，请稍后重试",
                AiProviderException.Category.REJECTED, false);
    }

    private static AiProviderException failure(int code, String message,
                                               AiProviderException.Category category,
                                               boolean failoverAllowed) {
        return new AiProviderException(code, message, category, failoverAllowed);
    }

    static String valueAsString(Object value) {
        return value instanceof String text ? text : "";
    }
}
