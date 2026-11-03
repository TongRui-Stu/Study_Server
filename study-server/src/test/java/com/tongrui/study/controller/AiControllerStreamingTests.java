package com.tongrui.study.controller;

import com.tongrui.study.client.AiStreamListener;
import com.tongrui.study.client.AiStreamResult;
import com.tongrui.study.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerStreamingTests {

    private AiService aiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiController(aiService, new SyncTaskExecutor()))
                .build();
    }

    @Test
    void streamUsesOneRequestIdAcrossStartSourceDeltaAndDone() throws Exception {
        when(aiService.chatStream(any(), any(), any())).thenAnswer(invocation -> {
            AiStreamListener listener = invocation.getArgument(2);
            listener.onSource("deepseek", "deepseek-v4.1-flash");
            listener.onDelta("流式内容");
            return new AiStreamResult("deepseek-v4.1-flash", "deepseek");
        });

        String body = performStream();

        assertTrue(body.contains("event:start"));
        assertTrue(body.contains("event:source"));
        assertTrue(body.contains("event:delta"));
        assertTrue(body.contains("event:done"));
        assertTrue(body.contains("\"provider\":\"deepseek\""));
        assertTrue(body.contains("\"model\":\"deepseek-v4.1-flash\""));
        assertTrue(body.contains("\"delta\":\"流式内容\""));

        Matcher matcher = Pattern.compile("\\\"requestId\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        Set<String> requestIds = new HashSet<>();
        int occurrences = 0;
        while (matcher.find()) {
            requestIds.add(matcher.group(1));
            occurrences++;
        }
        assertEquals(4, occurrences);
        assertEquals(1, requestIds.size());
    }

    @Test
    void unexpectedFailureReturnsOnlySafeErrorEvent() throws Exception {
        when(aiService.chatStream(any(), any(), any()))
                .thenThrow(new IllegalStateException("secret upstream response"));

        String body = performStream();

        assertTrue(body.contains("event:start"));
        assertTrue(body.contains("event:error"));
        assertTrue(body.contains("AI服务暂时不可用，请稍后重试"));
        assertFalse(body.contains("secret upstream response"));
        assertFalse(body.contains("event:done"));
    }

    private String performStream() throws Exception {
        MvcResult initial = mockMvc.perform(post("/api/ai/chat/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"message\":\"请解释线程\",\"history\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
