package com.tutuchacha.ai.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 模型接入配置（百炼 OpenAI 兼容模式）。
 * api-key 只从环境变量注入，禁止写进仓库。
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        Double temperature,
        Integer maxTokens,
        Duration timeout
) {
    public String baseUrlOrDefault() {
        String url = baseUrl == null || baseUrl.isBlank()
                ? "https://dashscope.aliyuncs.com/compatible-mode"
                : baseUrl.trim();
        // Spring AI 自己会拼 /v1/chat/completions，容忍环境变量多写的 /v1
        return url.endsWith("/v1") ? url.substring(0, url.length() - 3) : url;
    }

    public String chatModelOrDefault() {
        return chatModel == null || chatModel.isBlank() ? "qwen-plus" : chatModel;
    }

    public double temperatureOrDefault() {
        return temperature == null ? 0.7 : temperature;
    }

    public int maxTokensOrDefault() {
        return maxTokens == null ? 4096 : maxTokens;
    }

    /** 单轮模型调用上限，默认 60s；0 或负数表示不额外加超时。 */
    public Duration timeoutOrDefault() {
        return timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(60) : timeout;
    }
}
