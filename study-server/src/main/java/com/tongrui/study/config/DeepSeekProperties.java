package com.tongrui.study.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.deepseek")
public class DeepSeekProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-flash";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;
}
