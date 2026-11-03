package com.tongrui.study.client;

import com.tongrui.study.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AiChatGateway {

    private static final Logger log = LoggerFactory.getLogger(AiChatGateway.class);

    private final DeepSeekClient deepSeekClient;
    private final QwenClient qwenClient;

    public AiChatGateway(DeepSeekClient deepSeekClient, QwenClient qwenClient) {
        this.deepSeekClient = deepSeekClient;
        this.qwenClient = qwenClient;
    }

    public AiChatResult chat(List<AiChatMessage> messages, int maxTokens) {
        if (!deepSeekClient.isConfigured()) {
            if (!qwenClient.isConfigured()) {
                throw new BusinessException(503, "AI服务未配置，请联系管理员");
            }
            log.info("AI provider={} category={} failover={}",
                    deepSeekClient.providerName(), AiProviderException.Category.NOT_CONFIGURED, true);
            return callFallback(messages, maxTokens);
        }

        try {
            return deepSeekClient.chat(messages, maxTokens);
        } catch (AiProviderException exception) {
            boolean shouldFailover = exception.isFailoverAllowed() && qwenClient.isConfigured();
            log.warn("AI provider={} category={} failover={}",
                    deepSeekClient.providerName(), exception.getCategory(), shouldFailover);
            if (!shouldFailover) {
                throw exception;
            }
            return callFallback(messages, maxTokens);
        }
    }

    public AiStreamResult stream(List<AiChatMessage> messages, int maxTokens,
                                 AiStreamListener listener) {
        if (!deepSeekClient.isConfigured()) {
            if (!qwenClient.isConfigured()) {
                throw new BusinessException(503, "AI服务未配置，请联系管理员");
            }
            log.info("AI stream provider={} category={} failover={}",
                    deepSeekClient.providerName(), AiProviderException.Category.NOT_CONFIGURED, true);
            return streamFallback(messages, maxTokens, listener);
        }

        AtomicBoolean emitted = new AtomicBoolean(false);
        try {
            return streamProvider(deepSeekClient, messages, maxTokens, listener, emitted);
        } catch (AiProviderException exception) {
            if (emitted.get()) {
                log.warn("AI stream provider={} category={} failover={}",
                        deepSeekClient.providerName(), exception.getCategory(), false);
                throw new BusinessException(503, "生成中断，请重新生成");
            }
            boolean shouldFailover = exception.isFailoverAllowed() && qwenClient.isConfigured();
            log.warn("AI stream provider={} category={} failover={}",
                    deepSeekClient.providerName(), exception.getCategory(), shouldFailover);
            if (!shouldFailover) {
                throw exception;
            }
            return streamFallback(messages, maxTokens, listener);
        }
    }

    private AiChatResult callFallback(List<AiChatMessage> messages, int maxTokens) {
        try {
            return qwenClient.chat(messages, maxTokens);
        } catch (AiProviderException exception) {
            log.warn("AI provider={} category={} failover={}",
                    qwenClient.providerName(), exception.getCategory(), false);
            throw new BusinessException(503, "AI服务暂时不可用，请稍后重试");
        }
    }

    private AiStreamResult streamFallback(List<AiChatMessage> messages, int maxTokens,
                                          AiStreamListener listener) {
        AtomicBoolean emitted = new AtomicBoolean(false);
        try {
            return streamProvider(qwenClient, messages, maxTokens, listener, emitted);
        } catch (AiProviderException exception) {
            log.warn("AI stream provider={} category={} failover={}",
                    qwenClient.providerName(), exception.getCategory(), false);
            throw new BusinessException(503, emitted.get()
                    ? "生成中断，请重新生成"
                    : "AI服务暂时不可用，请稍后重试");
        }
    }

    private AiStreamResult streamProvider(AiProviderClient provider, List<AiChatMessage> messages,
                                          int maxTokens, AiStreamListener listener,
                                          AtomicBoolean emitted) {
        return provider.stream(messages, maxTokens, chunk -> {
            if (emitted.compareAndSet(false, true)) {
                listener.onSource(provider.providerName(), chunk.model());
            }
            listener.onDelta(chunk.content());
        });
    }
}
