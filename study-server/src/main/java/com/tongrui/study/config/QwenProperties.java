package com.tongrui.study.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.qwen")
public class QwenProperties {

    private String apiKey = "";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String model = "qwen3.8-flash";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;
}
