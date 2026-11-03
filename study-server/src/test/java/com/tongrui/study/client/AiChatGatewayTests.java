package com.tongrui.study.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.config.DeepSeekProperties;
import com.tongrui.study.config.QwenProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatGatewayTests {

    private static final String TEST_DEEPSEEK_KEY = "unit-deepseek-key";
    private static final String TEST_QWEN_KEY = "unit-qwen-key";

    private HttpServer deepSeekServer;
    private HttpServer qwenServer;
    private DeepSeekProperties deepSeekProperties;
    private QwenProperties qwenProperties;
    private final AtomicInteger deepSeekStatus = new AtomicInteger(200);
    private final AtomicInteger qwenStatus = new AtomicInteger(200);
    private final AtomicInteger deepSeekCalls = new AtomicInteger();
    private final AtomicInteger qwenCalls = new AtomicInteger();
    private final AtomicLong deepSeekDelayMs = new AtomicLong();
    private final AtomicReference<String> deepSeekBody = new AtomicReference<>();
    private final AtomicReference<String> qwenBody = new AtomicReference<>();
    private final AtomicReference<String> qwenRequestBody = new AtomicReference<>();
    private final AtomicReference<String> qwenAuthorization = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        deepSeekServer = createServer("/chat/completions", this::handleDeepSeek);
        qwenServer = createServer("/compatible-mode/v1/chat/completions", this::handleQwen);

        deepSeekProperties = new DeepSeekProperties();
        deepSeekProperties.setApiKey(TEST_DEEPSEEK_KEY);
        deepSeekProperties.setBaseUrl(baseUrl(deepSeekServer));
        deepSeekProperties.setModel("deepseek-test");
        deepSeekProperties.setConnectTimeoutMs(500);
        deepSeekProperties.setReadTimeoutMs(500);

        qwenProperties = new QwenProperties();
        qwenProperties.setApiKey(TEST_QWEN_KEY);
        qwenProperties.setBaseUrl(baseUrl(qwenServer) + "/compatible-mode/v1");
        qwenProperties.setModel("qwen-test");
        qwenProperties.setConnectTimeoutMs(500);
        qwenProperties.setReadTimeoutMs(500);

        deepSeekBody.set(success("deepseek-test", "主服务回答"));
        qwenBody.set(success("qwen-test", "备用服务回答"));
    }

    @AfterEach
    void tearDown() {
        deepSeekServer.stop(0);
        qwenServer.stop(0);
    }

    @Test
    void deepSeekSuccessNeverCallsQwen() {
        AiChatResult result = gateway().chat(messages(), 600);

        assertEquals("主服务回答", result.content());
        assertEquals("deepseek-test", result.model());
        assertEquals("deepseek", result.provider());
        assertEquals(1, deepSeekCalls.get());
        assertEquals(0, qwenCalls.get());
    }

    @Test
    void failoverEligibleHttpStatusesCallQwenOnce() {
        for (int status : List.of(429, 500, 503)) {
            resetCalls();
            deepSeekStatus.set(status);
            deepSeekBody.set(sensitiveError());

            AiChatResult result = gateway().chat(messages(), 600);

            assertEquals("备用服务回答", result.content());
            assertEquals(1, deepSeekCalls.get());
            assertEquals(1, qwenCalls.get());
        }
    }

    @Test
    void readTimeoutCallsQwenOnce() {
        deepSeekProperties.setReadTimeoutMs(30);
        deepSeekDelayMs.set(180);

        AiChatResult result = gateway().chat(messages(), 600);

        assertEquals("备用服务回答", result.content());
        assertEquals(1, qwenCalls.get());
    }

    @Test
    void connectionFailureCallsQwenOnce() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        deepSeekProperties.setBaseUrl("http://127.0.0.1:" + unusedPort);

        AiChatResult result = gateway().chat(messages(), 600);

        assertEquals("备用服务回答", result.content());
        assertEquals(1, qwenCalls.get());
    }

    @Test
    void invalidOrEmptyDeepSeekResponseCallsQwen() {
        for (String body : List.of(
                "not-json",
                "{\"model\":\"deepseek-test\",\"choices\":[]}",
                "{\"model\":\"deepseek-test\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\" \"}}]}")) {
            resetCalls();
            deepSeekStatus.set(200);
            deepSeekBody.set(body);

            assertEquals("备用服务回答", gateway().chat(messages(), 600).content());
            assertEquals(1, qwenCalls.get());
        }
    }

    @Test
    void nonFailoverHttpStatusesDoNotCallQwen() {
        for (int status : List.of(400, 401, 402, 403, 404, 422)) {
            resetCalls();
            deepSeekStatus.set(status);
            deepSeekBody.set(sensitiveError());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> gateway().chat(messages(), 600));

            assertEquals(0, qwenCalls.get());
            assertFalse(exception.getMessage().contains("sensitive-upstream-detail"));
            assertFalse(exception.getMessage().contains(TEST_DEEPSEEK_KEY));
            assertFalse(exception.getMessage().contains(TEST_QWEN_KEY));
        }
    }

    @Test
    void rejectedFinishReasonDoesNotCallQwen() {
        deepSeekBody.set("{\"model\":\"deepseek-test\",\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":\"\"}}]}");

        assertThrows(BusinessException.class, () -> gateway().chat(messages(), 600));

        assertEquals(0, qwenCalls.get());
    }

    @Test
    void missingDeepSeekUsesQwenAndSendsProviderSpecificRequest() {
        deepSeekProperties.setApiKey("");

        AiChatResult result = gateway().chat(messages(), 777);

        assertEquals("备用服务回答", result.content());
        assertEquals("qwen-test", result.model());
        assertEquals("qwen", result.provider());
        assertEquals(0, deepSeekCalls.get());
        assertEquals(1, qwenCalls.get());
        assertEquals("Bearer " + TEST_QWEN_KEY, qwenAuthorization.get());
        assertTrue(qwenRequestBody.get().contains("\"model\":\"qwen-test\""));
        assertTrue(qwenRequestBody.get().contains("\"messages\""));
        assertTrue(qwenRequestBody.get().contains("\"stream\":false"));
        assertTrue(qwenRequestBody.get().contains("\"max_tokens\":777"));
        assertTrue(qwenRequestBody.get().contains("\"enable_thinking\":false"));
        assertFalse(qwenRequestBody.get().contains("\"thinking\""));
    }

    @Test
    void bothMissingReturnsExplicitConfigurationError() {
        deepSeekProperties.setApiKey("");
        qwenProperties.setApiKey("");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> gateway().chat(messages(), 600));

        assertTrue(exception.getMessage().contains("未配置"));
        assertEquals(0, deepSeekCalls.get());
        assertEquals(0, qwenCalls.get());
    }

    @Test
    void bothFailReturnsUnifiedSanitizedErrorAndNeverRetries() {
        deepSeekStatus.set(503);
        qwenStatus.set(503);
        deepSeekBody.set(sensitiveError());
        qwenBody.set(sensitiveError());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> gateway().chat(messages(), 600));

        assertEquals(1, deepSeekCalls.get());
        assertEquals(1, qwenCalls.get());
        assertEquals("AI服务暂时不可用，请稍后重试", exception.getMessage());
        assertFalse(exception.getMessage().contains("sensitive-upstream-detail"));
    }

    @Test
    void qwenParsesNormalLengthAndRejectsInvalidResponses() {
        QwenClient client = new QwenClient(qwenProperties);
        assertEquals("备用服务回答", client.chat(messages(), 600).content());

        qwenBody.set("{\"model\":\"qwen-test\",\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"未完成\"}}]}");
        assertTrue(client.chat(messages(), 600).content().contains("可以继续追问"));

        qwenBody.set("{\"model\":\"qwen-test\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\" \"}}]}");
        assertThrows(AiProviderException.class, () -> client.chat(messages(), 600));

        qwenBody.set("not-json");
        assertThrows(AiProviderException.class, () -> client.chat(messages(), 600));

        qwenBody.set("{\"model\":\"qwen-test\",\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":\"内容\"}}]}");
        AiProviderException rejected = assertThrows(AiProviderException.class,
                () -> client.chat(messages(), 600));
        assertFalse(rejected.isFailoverAllowed());
    }

    @Test
    void actualModelWinsAndMissingModelFallsBackToConfiguredModel() {
        deepSeekBody.set(success("deepseek-v4.1-flash", "回答"));
        assertEquals("deepseek-v4.1-flash", gateway().chat(messages(), 600).model());
        deepSeekBody.set("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"回答\"}}]}");
        assertEquals("deepseek-test", gateway().chat(messages(), 600).model());
        deepSeekStatus.set(503);
        qwenBody.set(success("qwen3.8-flash", "备用"));
        AiChatResult fallback = gateway().chat(messages(), 600);
        assertEquals("qwen3.8-flash", fallback.model());
        assertEquals("qwen", fallback.provider());
        qwenBody.set(success("", "备用"));
        assertEquals("qwen-test", gateway().chat(messages(), 600).model());
    }

    private AiChatGateway gateway() {
        return new AiChatGateway(new DeepSeekClient(deepSeekProperties), new QwenClient(qwenProperties));
    }

    private HttpServer createServer(String path, ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> handler.handle(exchange));
        server.start();
        return server;
    }

    private void handleDeepSeek(HttpExchange exchange) throws IOException {
        deepSeekCalls.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        long delay = deepSeekDelayMs.get();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        respond(exchange, deepSeekStatus.get(), deepSeekBody.get());
    }

    private void handleQwen(HttpExchange exchange) throws IOException {
        qwenCalls.incrementAndGet();
        qwenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        qwenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, qwenStatus.get(), qwenBody.get());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void resetCalls() {
        deepSeekCalls.set(0);
        qwenCalls.set(0);
    }

    private List<AiChatMessage> messages() {
        return List.of(new AiChatMessage("system", "规则"), new AiChatMessage("user", "问题"));
    }

    private String success(String model, String content) {
        return "{\"model\":\"" + model + "\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\""
                + content + "\"}}]}";
    }

    private String sensitiveError() {
        return "{\"error\":{\"message\":\"sensitive-upstream-detail\"}}";
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
