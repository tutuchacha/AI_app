package com.tutuchacha.ai.gateway;

/**
 * 流式增量。content 为空但带 token 数的最后一帧表示「用量统计」。
 */
public record AiStreamChunk(
        String content,
        String model,
        Integer promptTokens,
        Integer completionTokens
) {
    public boolean hasUsage() {
        return promptTokens != null || completionTokens != null;
    }
}
