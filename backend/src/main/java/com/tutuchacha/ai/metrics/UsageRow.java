package com.tutuchacha.ai.metrics;

import java.time.LocalDateTime;

/** 用量聚合的原始行：只取统计需要的列，不查正文。 */
public record UsageRow(
        Integer tokensIn,
        Integer tokensOut,
        Integer latencyMs,
        String status,
        LocalDateTime createdAt,
        String module
) {
}
