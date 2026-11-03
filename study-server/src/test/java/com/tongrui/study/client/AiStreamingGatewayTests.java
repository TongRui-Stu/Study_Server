package com.tongrui.study.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.config.DeepSeekProperties;
import com.tongrui.study.config.QwenProperties;
import com.tongrui.study.controller.AiController;
import com.tongrui.study.service.AiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiStreamingGatewayTests {

    private HttpServer deepSeekServer;
    private HttpServer qwenServer;
    private DeepSeekProperties deepSeekProperties;
    private QwenProperties qwenProperties;
    private final AtomicInteger deepSeekStatus = new AtomicInteger(200);
    private final AtomicInteger qwenStatus = new AtomicInteger(200);
    private final AtomicInteger deepSeekCalls = new AtomicInteger();
    private final AtomicInteger qwenCalls = new AtomicInteger();
    private final AtomicReference<String> deepSeekBody = new AtomicReference<>();
    private final AtomicReference<String> qwenBody = new AtomicReference<>();
    private final AtomicReference<String> deepSeekRequest = new AtomicReference<>();
    private final AtomicReference<String> qwenRequest = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        deepSeekServer = server("/chat/completions", exchange -> respond(
                exchange, deepSeekStatus.get(), deepSeekBody.get(), deepSeekCalls, deepSeekRequest));
        qwenServer = server("/compatible-mode/v1/chat/completions", exchange -> respond(
                exchange, qwenStatus.get(), qwenBody.get(), qwenCalls, qwenRequest));

        deepSeekProperties = new DeepSeekProperties();
        deepSeekProperties.setApiKey("test-deepseek-key");
        deepSeekProperties.setBaseUrl(baseUrl(deepSeekServer));
        deepSeekProperties.setModel("deepseek-test");
        deepSeekProperties.setConnectTimeoutMs(1000);
        deepSeekProperties.setReadTimeoutMs(1000);

        qwenProperties = new QwenProperties();
        qwenProperties.setApiKey("test-qwen-key");
        qwenProperties.setBaseUrl(baseUrl(qwenServer) + "/compatible-mode/v1");
        qwenProperties.setModel("qwen-test");
        qwenProperties.setConnectTimeoutMs(1000);
        qwenProperties.setReadTimeoutMs(1000);

        deepSeekBody.set(stream("deepseek-v4.1-flash", "主", "服务"));
        qwenBody.set(stream("qwen3.8-flash", "备", "用"));
    }

    @AfterEach
    void tearDown() {
        if (deepSeekServer != null) deepSeekServer.stop(0);
        if (qwenServer != null) qwenServer.stop(0);
    }

    @Test
    void deepSeekStreamsContentIgnoresEmptyAndUsageAndHonorsDone() {
        List<String> chunks = new ArrayList<>();

        AiStreamResult result = new DeepSeekClient(deepSeekProperties)
                .stream(messages(), 456, chunk -> chunks.add(chunk.content()));

        assertEquals(List.of("主", "服务"), chunks);
        assertEquals("deepseek-v4.1-flash", result.model());
        assertEquals("deepseek", result.provider());
        assertTrue(deepSeekRequest.get().contains("\"stream\":true"));
        assertTrue(deepSeekRequest.get().contains("\"thinking\":{\"type\":\"disabled\"}"));
        assertTrue(deepSeekRequest.get().contains("\"max_tokens\":456"));
    }

    @Test
    void qwenStreamsContentWithThinkingDisabled() {
        List<String> chunks = new ArrayList<>();

        AiStreamResult result = new QwenClient(qwenProperties)
                .stream(messages(), 789, chunk -> chunks.add(chunk.content()));

        assertEquals(List.of("备", "用"), chunks);
        assertEquals("qwen3.8-flash", result.model());
        assertEquals("qwen", result.provider());
        assertTrue(qwenRequest.get().contains("\"stream\":true"));
        assertTrue(qwenRequest.get().contains("\"enable_thinking\":false"));
        assertFalse(qwenRequest.get().contains("\"thinking\""));
    }

    @Test
    void failureBeforeFirstDeltaFallsBackWithoutDeepSeekResidue() {
        deepSeekBody.set("data: not-json\n\n");
        List<String> events = new ArrayList<>();

        AiStreamResult result = gateway().stream(messages(), 600, listener(events));

        assertEquals("qwen", result.provider());
        assertEquals(List.of("source:qwen:qwen3.8-flash", "delta:备", "delta:用"), events);
        assertEquals(1, deepSeekCalls.get());
        assertEquals(1, qwenCalls.get());
    }

    @Test
    void failureAfterDeltaStopsWithoutAppendingQwen() {
        deepSeekBody.set("""
                data: {"model":"deepseek-test","choices":[{"delta":{"content":"已输出"},"finish_reason":null}]}

                data: malformed-sensitive-upstream-detail

                """);
        List<String> events = new ArrayList<>();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> gateway().stream(messages(), 600, listener(events)));

        assertEquals("生成中断，请重新生成", exception.getMessage());
        assertEquals(List.of("source:deepseek:deepseek-test", "delta:已输出"), events);
        assertEquals(0, qwenCalls.get());
        assertFalse(exception.getMessage().contains("sensitive-upstream-detail"));
    }

    @Test
    void missingDoneIsConnectionInterruptionAndFallsBackOnlyBeforeContent() {
        deepSeekBody.set("data: {\"model\":\"deepseek-test\",\"choices\":[]}\n\n");
        List<String> events = new ArrayList<>();

        gateway().stream(messages(), 600, listener(events));

        assertEquals(List.of("source:qwen:qwen3.8-flash", "delta:备", "delta:用"), events);
        assertEquals(1, qwenCalls.get());
    }

    @Test
    void bothProvidersFailWithUnifiedSafeError() {
        deepSeekStatus.set(503);
        qwenStatus.set(503);
        deepSeekBody.set("sensitive-deepseek-detail");
        qwenBody.set("sensitive-qwen-detail");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> gateway().stream(messages(), 600, listener(new ArrayList<>())));

        assertEquals("AI服务暂时不可用，请稍后重试", exception.getMessage());
        assertFalse(exception.getMessage().contains("sensitive"));
        assertEquals(1, deepSeekCalls.get());
        assertEquals(1, qwenCalls.get());
    }

    @Test
    void rejectedResponseBeforeFirstDeltaDoesNotCallQwen() {
        deepSeekBody.set("""
                data: {"model":"deepseek-test","choices":[{"delta":{},"finish_reason":"content_filter"}]}

                data: [DONE]

                """);

        List<String> events = new ArrayList<>();

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> gateway().stream(messages(), 600, listener(events)));

        assertEquals(AiProviderException.Category.REJECTED, exception.getCategory());
        assertTrue(events.isEmpty());
        assertEquals(0, qwenCalls.get());
    }

    @Test
    void nonFailoverHttpStatusesBeforeFirstDeltaDoNotCallQwen() {
        for (int status : List.of(400, 401, 402, 403, 404, 422)) {
            deepSeekStatus.set(status);
            deepSeekBody.set("sensitive-upstream-detail");
            deepSeekCalls.set(0);
            qwenCalls.set(0);
            List<String> events = new ArrayList<>();

            AiProviderException exception = assertThrows(AiProviderException.class,
                    () -> gateway().stream(messages(), 600, listener(events)));

            assertFalse(exception.isFailoverAllowed());
            assertFalse(exception.getMessage().contains("sensitive-upstream-detail"));
            assertTrue(events.isEmpty());
            assertEquals(1, deepSeekCalls.get());
            assertEquals(0, qwenCalls.get(), "status=" + status);
        }
    }

    @Test
    void eligibleHttpStatusesBeforeFirstDeltaCallQwenOnce() {
        for (int status : List.of(429, 500, 503)) {
            deepSeekStatus.set(status);
            deepSeekCalls.set(0);
            qwenCalls.set(0);
            List<String> events = new ArrayList<>();

            AiStreamResult result = gateway().stream(messages(), 600, listener(events));

            assertEquals("qwen", result.provider());
            assertEquals(List.of("source:qwen:qwen3.8-flash", "delta:备", "delta:用"), events);
            assertEquals(1, deepSeekCalls.get());
            assertEquals(1, qwenCalls.get());
        }
    }

    @Test
    void successfulPrimaryStreamNeverCallsQwen() {
        List<String> events = new ArrayList<>();

        AiStreamResult result = gateway().stream(messages(), 600, listener(events));

        assertEquals("deepseek", result.provider());
        assertEquals(List.of("source:deepseek:deepseek-v4.1-flash", "delta:主", "delta:服务"), events);
        assertEquals(1, deepSeekCalls.get());
        assertEquals(0, qwenCalls.get());
    }

    @Test
    void eligibleFailureWithoutConfiguredFallbackReturnsOriginalError() {
        deepSeekStatus.set(429);
        qwenProperties.setApiKey("");
        List<String> events = new ArrayList<>();

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> gateway().stream(messages(), 600, listener(events)));

        assertEquals(AiProviderException.Category.RATE_LIMIT, exception.getCategory());
        assertTrue(events.isEmpty());
        assertEquals(1, deepSeekCalls.get());
        assertEquals(0, qwenCalls.get());
    }

    @Test
    void threeStreamingRoutesReturnSafeErrorForNonFailoverFailure() throws Exception {
        deepSeekStatus.set(401);
        deepSeekBody.set("sensitive-upstream-detail");
        for (String path : List.of("chat", "question-chat", "learning-analysis")) {
            deepSeekCalls.set(0);
            qwenCalls.set(0);

            String body = performStreamingRoute(path);

            assertTrue(body.contains("event:start"));
            assertTrue(body.contains("event:error"));
            assertTrue(body.contains("AI服务认证失败"));
            assertFalse(body.contains("event:source"));
            assertFalse(body.contains("event:delta"));
            assertFalse(body.contains("event:done"));
            assertFalse(body.contains("sensitive-upstream-detail"));
            assertEquals(1, deepSeekCalls.get());
            assertEquals(0, qwenCalls.get());
        }
    }

    @Test
    void threeStreamingRoutesExposeOnlyFallbackSourceForEligibleFailure() throws Exception {
        deepSeekStatus.set(429);
        for (String path : List.of("chat", "question-chat", "learning-analysis")) {
            deepSeekCalls.set(0);
            qwenCalls.set(0);

            String body = performStreamingRoute(path);

            assertTrue(body.contains("event:source"));
            assertTrue(body.contains("\"provider\":\"qwen\""));
            assertTrue(body.contains("\"model\":\"qwen3.8-flash\""));
            assertTrue(body.contains("event:delta"));
            assertTrue(body.contains("event:done"));
            assertFalse(body.contains("event:error"));
            assertFalse(body.contains("\"provider\":\"deepseek\""));
            assertEquals(1, deepSeekCalls.get());
            assertEquals(1, qwenCalls.get());
        }
    }

    private String performStreamingRoute(String path) throws Exception {
        // Real Controller -> gateway -> local HTTP providers; business context
        // preparation is isolated because this regression targets failover/SSE.
        AiService service = mock(AiService.class);
        when(service.chatStream(any(), any(), any())).thenAnswer(invocation ->
                gateway().stream(messages(), 600, invocation.getArgument(2)));
        when(service.questionChatStream(any(), any(), any())).thenAnswer(invocation ->
                gateway().stream(messages(), 600, invocation.getArgument(2)));
        when(service.learningAnalysisStream(any(), any(), any())).thenAnswer(invocation ->
                gateway().stream(messages(), 600, invocation.getArgument(2)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AiController(service, new SyncTaskExecutor())).build();
        String content = switch (path) {
            case "question-chat" -> "{\"answerRecordId\":1,\"message\":\"解释\",\"history\":[]}";
            case "learning-analysis" -> "{\"days\":7,\"message\":\"解释\",\"history\":[]}";
            default -> "{\"message\":\"解释\",\"history\":[]}";
        };
        MvcResult initial = mvc.perform(post("/api/ai/" + path + "/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content(content))
                .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(initial)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private AiChatGateway gateway() {
        return new AiChatGateway(new DeepSeekClient(deepSeekProperties), new QwenClient(qwenProperties));
    }

    private AiStreamListener listener(List<String> events) {
        return new AiStreamListener() {
            @Override
            public void onSource(String provider, String model) {
                events.add("source:" + provider + ":" + model);
            }

            @Override
            public void onDelta(String content) {
                events.add("delta:" + content);
            }
        };
    }

    private String stream(String model, String first, String second) {
        return "data: {\"model\":\"" + model + "\",\"choices\":[{\"delta\":{\"content\":\"" + first
                + "\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"model\":\"" + model + "\",\"choices\":[{\"delta\":{},\"finish_reason\":null}]}\n\n"
                + "data: {\"model\":\"" + model + "\",\"choices\":[{\"delta\":{\"content\":\"" + second
                + "\"},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: {\"model\":\"" + model + "\",\"choices\":[],\"usage\":{\"total_tokens\":2}}\n\n"
                + "data: [DONE]\n\n";
    }

    private List<AiChatMessage> messages() {
        return List.of(new AiChatMessage("system", "规则"), new AiChatMessage("user", "问题"));
    }

    private HttpServer server(String path, Handler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler::handle);
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, int status, String body,
                         AtomicInteger calls, AtomicReference<String> request) throws IOException {
        calls.incrementAndGet();
        request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
