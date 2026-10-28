package com.tutuchacha.ai.support;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 客服策略：前端按 minScore 判断本轮回答是否可信，
 * 低于阈值就展示「没查到 + 转人工」，而不是继续追问模型。
 */
@Schema(description = "智能客服策略")
public record SupportPolicy(
        @Schema(description = "最高引用分低于此值即视为没查到", example = "0.6") double minScore,
        @Schema(description = "客服检索条数", example = "5") int topK,
        @Schema(description = "是否展示转人工入口", example = "true") boolean handoffEnabled
) {
}
