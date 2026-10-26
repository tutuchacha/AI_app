package com.tutuchacha.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "非流式聊天请求")
public record ChatRequest(
        @Schema(description = "用户输入", example = "用一句话介绍你自己")
        @NotBlank(message = "内容不能为空")
        @Size(max = 4000, message = "单条消息最长 4000 字")
        String content,

        @Schema(description = "会话 ID，留空表示新会话", example = "c_8f3a2b1c9d0e")
        @Size(max = 64, message = "会话 ID 最长 64 位")
        String conversationId,

        @Schema(description = "业务模块：chat / companion / support / analysis", example = "chat")
        @Size(max = 16, message = "模块名最长 16 位")
        String module,

        @Schema(description = "模型名，留空用默认 qwen-plus", example = "qwen-plus")
        @Size(max = 64, message = "模型名最长 64 位")
        String model,

        @Schema(description = "重试失败的一轮：不再插入新的 user 消息，直接重新生成", example = "false")
        Boolean retry
) {
}
