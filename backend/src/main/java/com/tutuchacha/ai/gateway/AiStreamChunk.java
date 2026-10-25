package com.tutuchacha.ai.gateway;

/**
 * 流式增量：content 为空的最后一帧用来带 token 与结束原因。
 */
public record AiStreamChunk(
        String content,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        String finishReason
) {
    public AiStreamChunk(String content, String model, Integer promptTokens, Integer completionTokens) {
        this(content, model, promptTokens, completionTokens, null);
    }

    public boolean hasUsage() {
        return promptTokens != null || completionTokens != null;
    }
}
