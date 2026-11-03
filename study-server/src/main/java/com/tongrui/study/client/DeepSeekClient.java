package com.tongrui.study.client;

import com.tongrui.study.config.DeepSeekProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class DeepSeekClient implements AiProviderClient {

    private final DeepSeekProperties properties;
    private final RestClient restClient;

    @Autowired
    public DeepSeekClient(DeepSeekProperties properties) {
        this(properties, createRestClient(properties));
    }

    DeepSeekClient(DeepSeekProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String providerName() {
        return "deepseek";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getApiKey());
    }

    @Override
    public AiChatResult chat(List<AiChatMessage> messages, int maxTokens) {
        if (!isConfigured()) {
            throw AiProviderSupport.notConfigured();
        }

        Map<String, Object> body = requestBody(messages, maxTokens, false);

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(properties.getApiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientResponseException exception) {
            throw AiProviderSupport.mapHttpError(exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            throw AiProviderSupport.mapConnectionError(exception);
        } catch (RestClientException exception) {
            throw AiProviderSupport.malformedResponse();
        }

        return AiProviderSupport.parseResponse(response, properties.getModel(), providerName());
    }

    @Override
    public AiStreamResult stream(List<AiChatMessage> messages, int maxTokens,
                                 Consumer<AiStreamChunk> chunkConsumer) {
        if (!isConfigured()) {
            throw AiProviderSupport.notConfigured();
        }

        Map<String, Object> body = requestBody(messages, maxTokens, true);
        return AiProviderStreamingSupport.stream(restClient, properties.getApiKey(), body,
                properties.getModel(), providerName(), chunkConsumer);
    }

    private Map<String, Object> requestBody(List<AiChatMessage> messages, int maxTokens, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        body.put("thinking", Map.of("type", "disabled"));
        body.put("stream", stream);
        body.put("max_tokens", maxTokens);
        return body;
    }

    private static RestClient createRestClient(DeepSeekProperties properties) {
        if (properties.getConnectTimeoutMs() <= 0 || properties.getReadTimeoutMs() <= 0) {
            throw new IllegalArgumentException("DeepSeek timeouts must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
