package com.tongrui.study.client;

import java.util.List;
import java.util.function.Consumer;

public interface AiProviderClient {

    String providerName();

    boolean isConfigured();

    AiChatResult chat(List<AiChatMessage> messages, int maxTokens);

    AiStreamResult stream(List<AiChatMessage> messages, int maxTokens,
                          Consumer<AiStreamChunk> chunkConsumer);
}
