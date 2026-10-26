package com.tutuchacha.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 左侧会话列表的一行。 */
@Schema(description = "会话摘要")
public record ConversationSummary(
        @Schema(description = "对外会话 ID", example = "c_8f3a2b1c9d0e")
        String uid,

        @Schema(description = "标题，取首条用户消息前 30 字", example = "用一句话解释 RAG")
        String title,

        @Schema(description = "业务模块", example = "chat")
        String module,

        @Schema(description = "最后一条消息时间（本地时间，ISO-8601）", example = "2026-10-26T09:41:02")
        String lastMessageAt
) {
}
