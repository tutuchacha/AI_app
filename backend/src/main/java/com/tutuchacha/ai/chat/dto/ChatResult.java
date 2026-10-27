package com.tutuchacha.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "非流式聊天结果")
public record ChatResult(
        @Schema(description = "会话 ID，新会话由后端生成") String conversationId,
        @Schema(description = "模型完整回答") String content,
        @Schema(description = "实际模型名") String model,
        @Schema(description = "输入 token") int promptTokens,
        @Schema(description = "输出 token") int completionTokens,
        @Schema(description = "耗时（毫秒）") long latencyMs,
        @Schema(description = "引用的知识库片段，无 RAG 时为空") List<Citation> citations
) {
}
