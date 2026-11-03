package com.tongrui.study.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.config.DeepSeekProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekClientTests {

    private HttpServer server;
    private DeepSeekProperties properties;
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicLong responseDelayMs = new AtomicLong(0);
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", this::handleRequest);
        server.start();

        properties = new DeepSeekProperties();
        properties.setApiKey("test-key-not-real");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setModel("deepseek-flash");
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(1000);
        responseBody.set(successResponse("讲解内容"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void parsesNormalResponseAndSendsRequiredFields() {
        AiChatResult result = new DeepSeekClient(properties).chat(
                List.of(new AiChatMessage("user", "测试问题")), 800);

        assertEquals("讲解内容", result.content());
        assertEquals("deepseek-flash", result.model());
        assertTrue(requestBody.get().contains("\"thinking\":{\"type\":\"disabled\"}"));
        assertTrue(requestBody.get().contains("\"stream\":false"));
        assertTrue(requestBody.get().contains("\"max_tokens\":800"));
    }

    @Test
    void rejectsMissingKeyWithoutCallingUpstream() {
        properties.setApiKey("");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new DeepSeekClient(properties).chat(List.of(new AiChatMessage("user", "问题")), 100));
        assertEquals(503, exception.getCode());
        assertTrue(exception.getMessage().contains("未配置"));
    }

    @Test
    void rejectsInvalidResponsesButKeepsContentWhenLengthLimited() {
        DeepSeekClient client = new DeepSeekClient(properties);

        responseBody.set("{\"model\":\"deepseek-flash\",\"choices\":[]}");
        assertEquals(502, assertThrows(BusinessException.class,
                () -> client.chat(messages(), 100)).getCode());

        responseBody.set("{\"model\":\"deepseek-flash\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\" \"}}]}");
        assertTrue(assertThrows(BusinessException.class,
                () -> client.chat(messages(), 100)).getMessage().contains("空内容"));

        responseBody.set("{\"model\":\"deepseek-flash\",\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"未完成\"}}]}");
        AiChatResult truncated = client.chat(messages(), 100);
        assertTrue(truncated.content().contains("未完成"));
        assertTrue(truncated.content().contains("可以继续追问"));

        responseBody.set("{\"model\":\"deepseek-flash\",\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":\"内容\"}}]}");
        assertTrue(assertThrows(BusinessException.class,
                () -> client.chat(messages(), 100)).getMessage().contains("未正常完成"));
    }

    @Test
    void mapsKnownHttpErrorsWithoutReturningRawBody() {
        DeepSeekClient client = new DeepSeekClient(properties);
        int[] statuses = {400, 401, 402, 422, 429, 500, 503};
        for (int status : statuses) {
            responseStatus.set(status);
            responseBody.set("{\"error\":{\"message\":\"secret upstream detail\"}}");
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> client.chat(messages(), 100));
            assertTrue(exception.getCode() >= 500);
            assertTrue(!exception.getMessage().contains("secret upstream detail"));
        }
    }

    @Test
    void mapsReadTimeout() {
        properties.setReadTimeoutMs(50);
        responseDelayMs.set(250);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new DeepSeekClient(properties).chat(messages(), 100));
        assertEquals(504, exception.getCode());
        assertTrue(exception.getMessage().contains("超时"));
    }

    @Test
    void mapsMalformedJson() {
        responseBody.set("not-json");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new DeepSeekClient(properties).chat(messages(), 100));
        assertEquals(502, exception.getCode());
        assertTrue(exception.getMessage().contains("响应格式异常"));
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        long delay = responseDelayMs.get();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private List<AiChatMessage> messages() {
        return List.of(new AiChatMessage("user", "问题"));
    }

    private String successResponse(String content) {
        return "{\"model\":\"deepseek-flash\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\""
                + content + "\"}}]}";
    }
}
