package com.tutuchacha.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SSE 事件（也是未来 WebSocket 帧的统一结构）。
 * 字段为空时不输出，前端按 type 分支处理。
 */
@Schema(description = "聊天流事件：meta / delta / usage / done / error")
public record ChatEvent(
        @Schema(description = "事件类型") String type,
        @Schema(description = "本次请求 ID") String requestId,
        @Schema(description = "会话 ID") String conversationId,
        @Schema(description = "消息 ID（M1-05 落库后返回）") String messageId,
        @Schema(description = "模型名") String model,
        @Schema(description = "delta 增量文本") String content,
        @Schema(description = "输入 token") Integer promptTokens,
        @Schema(description = "输出 token") Integer completionTokens,
        @Schema(description = "结束原因") String finishReason,
        @Schema(description = "整体耗时（毫秒）") Long latencyMs,
        @Schema(description = "错误码") String code,
        @Schema(description = "错误提示") String message
) {

    public static ChatEvent meta(String conversationId, String requestId, String model) {
        return new ChatEvent("meta", requestId, conversationId, null, model,
                null, null, null, null, null, null, null);
    }

    public static ChatEvent delta(String content) {
        return new ChatEvent("delta", null, null, null, null,
                content, null, null, null, null, null, null);
    }

    public static ChatEvent usage(int promptTokens, int completionTokens) {
        return new ChatEvent("usage", null, null, null, null,
                null, promptTokens, completionTokens, null, null, null, null);
    }

    public static ChatEvent done(String finishReason, long latencyMs) {
        return new ChatEvent("done", null, null, null, null,
                null, null, null, finishReason, latencyMs, null, null);
    }

    public static ChatEvent error(String code, String message) {
        return new ChatEvent("error", null, null, null, null,
                null, null, null, null, null, code, message);
    }
}
