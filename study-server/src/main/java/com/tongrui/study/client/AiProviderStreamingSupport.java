package com.tongrui.study.client;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class AiProviderStreamingSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TRUNCATED_NOTICE = "\n\n> 回答因长度限制已截断，你可以继续追问。";

    private AiProviderStreamingSupport() {
    }

    static AiStreamResult stream(RestClient restClient, String apiKey, Map<String, Object> body,
                                 String configuredModel, String provider,
                                 Consumer<AiStreamChunk> chunkConsumer) {
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw AiProviderSupport.mapHttpError(response.getStatusCode().value());
                        }
                        return parse(response.getBody(), configuredModel, provider, chunkConsumer);
                    });
        } catch (AiProviderException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw AiProviderSupport.mapConnectionError(exception);
        } catch (RestClientException exception) {
            throw AiProviderSupport.mapConnectionError(exception);
        }
    }

    private static AiStreamResult parse(java.io.InputStream inputStream, String configuredModel,
                                        String provider, Consumer<AiStreamChunk> chunkConsumer) {
        StreamState state = new StreamState(configuredModel);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    processEvent(data, state, chunkConsumer);
                    data.setLength(0);
                    if (state.done) {
                        break;
                    }
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).stripLeading());
                }
            }
            if (!state.done && !data.isEmpty()) {
                processEvent(data, state, chunkConsumer);
            }
        } catch (AiProviderException exception) {
            throw exception;
        } catch (IOException exception) {
            throw AiProviderSupport.mapConnectionError(exception);
        }

        if (!state.done) {
            throw AiProviderSupport.malformedResponse();
        }
        if (!state.hasContent) {
            throw AiProviderSupport.emptyResponse();
        }
        return new AiStreamResult(state.model, provider);
    }

    private static void processEvent(StringBuilder data, StreamState state,
                                     Consumer<AiStreamChunk> chunkConsumer) {
        if (data.isEmpty()) {
            return;
        }
        String payloadText = data.toString().trim();
        if ("[DONE]".equals(payloadText)) {
            state.done = true;
            return;
        }

        Map<?, ?> payload;
        try {
            payload = OBJECT_MAPPER.readValue(payloadText, Map.class);
        } catch (RuntimeException exception) {
            throw AiProviderSupport.malformedResponse();
        }
        String responseModel = AiProviderSupport.valueAsString(payload.get("model"));
        if (StringUtils.hasText(responseModel)) {
            state.model = responseModel;
        }

        Object choicesValue = payload.get("choices");
        if (choicesValue == null && payload.containsKey("usage")) {
            return;
        }
        if (!(choicesValue instanceof List<?> choices)) {
            throw AiProviderSupport.malformedResponse();
        }
        if (choices.isEmpty()) {
            return;
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            throw AiProviderSupport.malformedResponse();
        }

        String finishReason = AiProviderSupport.valueAsString(choice.get("finish_reason"));
        if (StringUtils.hasText(finishReason)
                && !"stop".equals(finishReason) && !"length".equals(finishReason)) {
            throw AiProviderSupport.rejectedResponse();
        }
        Object deltaValue = choice.get("delta");
        if (deltaValue != null && !(deltaValue instanceof Map<?, ?>)) {
            throw AiProviderSupport.malformedResponse();
        }
        if (deltaValue instanceof Map<?, ?> delta) {
            String content = AiProviderSupport.valueAsString(delta.get("content"));
            if (!content.isEmpty()) {
                state.hasContent = true;
                chunkConsumer.accept(new AiStreamChunk(content, state.model));
            }
        }
        if ("length".equals(finishReason) && !state.truncatedNoticeSent) {
            state.truncatedNoticeSent = true;
            state.hasContent = true;
            chunkConsumer.accept(new AiStreamChunk(TRUNCATED_NOTICE, state.model));
        }
    }

    private static final class StreamState {
        private String model;
        private boolean hasContent;
        private boolean done;
        private boolean truncatedNoticeSent;

        private StreamState(String configuredModel) {
            this.model = configuredModel;
        }
    }
}
