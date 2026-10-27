package com.tutuchacha.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 会话详情：刷新页面或点左侧列表时用来恢复聊天记录。 */
@Schema(description = "会话详情与历史消息")
public record ConversationDetail(
        @Schema(description = "对外会话 ID", example = "c_8f3a2b1c9d0e")
        String uid,

        @Schema(description = "标题", example = "用一句话解释 RAG")
        String title,

        @Schema(description = "业务模块", example = "chat")
        String module,

        @Schema(description = "会话绑定的知识库 ID，未绑定为空")
        Long kbId,

        @Schema(description = "按时间正序排列的消息")
        List<Item> messages
) {

    @Schema(description = "一条历史消息")
    public record Item(
            @Schema(description = "消息 ID") Long id,
            @Schema(description = "user / assistant") String role,
            @Schema(description = "消息内容") String content,
            @Schema(description = "DONE / STOPPED") String status,
            @Schema(description = "输入 token") Integer tokensIn,
            @Schema(description = "输出 token") Integer tokensOut,
            @Schema(description = "耗时毫秒") Integer latencyMs,
            @Schema(description = "模型名") String model,
            @Schema(description = "引用的知识库片段（刷新后恢复）") List<Citation> citations,
            @Schema(description = "创建时间（ISO-8601）") String createdAt
    ) {
    }
}
