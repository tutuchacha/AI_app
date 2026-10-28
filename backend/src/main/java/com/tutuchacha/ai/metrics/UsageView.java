package com.tutuchacha.ai.metrics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M3-05 用量看板数据：today/mine 同时给，前端可按用户视角或全局视角渲染。
 * source 说明口径：db = 只统计库里 message；prometheus+db = 指标来自 Prometheus。
 */
public record UsageView(
        String source,
        boolean prometheusUp,
        int activeStreams,
        LocalDateTime generatedAt,
        Totals today,
        Totals mine,
        List<DailyPoint> daily,
        List<ModuleShare> modules
) {

    /** 调用次数、token、P95 延迟、错误率。 */
    public record Totals(
            long calls,
            long tokensIn,
            long tokensOut,
            double p95Ms,
            double errorRate
    ) {
    }

    /** 单日调用量；label 给图表用（MM-dd），date 给排序与 tooltip。 */
    public record DailyPoint(String date, String label, long calls) {
    }

    /** 模块占比；label 是中文名，module 是库里的原始值。 */
    public record ModuleShare(String module, String label, long calls, double share) {
    }
}
