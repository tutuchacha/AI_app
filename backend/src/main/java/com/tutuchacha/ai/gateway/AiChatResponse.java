package com.tutuchacha.ai.gateway;

/** 模型返回结果，已抹平厂商差异。 */
public record AiChatResponse(
        String content,
        String model,
        int promptTokens,
        int completionTokens,
        long latencyMs
) {
}
