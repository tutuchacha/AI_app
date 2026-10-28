package com.tutuchacha.ai.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M4 智能客服配置：检索参数与「答不出来就转人工」的置信阈值。
 * 阈值放在后端，前端只读 /api/support/policy，调参不用改前端。
 */
@ConfigurationProperties(prefix = "app.support")
public record SupportProperties(
        Double minScore,
        Integer topK,
        Boolean handoffEnabled
) {

    /** 最高引用分低于它就当没查到；默认 0.6（实测相关命中 0.74+，无关提问 0.46~0.51）。 */
    public double minScoreOrDefault() {
        if (minScore == null || minScore <= 0 || minScore > 1) {
            return 0.6;
        }
        return minScore;
    }

    public int topKOrDefault() {
        return topK == null || topK < 1 ? 5 : topK;
    }

    public boolean handoffEnabledOrDefault() {
        return handoffEnabled == null || handoffEnabled;
    }
}
