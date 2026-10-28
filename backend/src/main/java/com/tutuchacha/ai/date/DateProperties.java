package com.tutuchacha.ai.date;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 恋爱助手配置：高德 key 只在后端使用，前端永远拿不到。 */
@ConfigurationProperties(prefix = "app.date")
public record DateProperties(
        String amapKey,
        String amapBaseUrl,
        Integer spotLimit,
        Integer askSpotLimit
) {

    public String amapKeyOrEmpty() {
        return amapKey == null ? "" : amapKey.trim();
    }

    public String amapBaseUrlOrDefault() {
        return amapBaseUrl == null || amapBaseUrl.isBlank() ? "https://restapi.amap.com" : amapBaseUrl.trim();
    }

    /** 每次建议最多带几个地点，默认 2，上限 4，避免前端卡片太长。 */
    public int spotLimitOrDefault() {
        if (spotLimit == null || spotLimit < 1) {
            return 2;
        }
        return Math.min(spotLimit, 4);
    }

    /** 问答式推荐默认多给几家，默认 4，上限 6。 */
    public int askSpotLimitOrDefault() {
        if (askSpotLimit == null || askSpotLimit < 1) {
            return 4;
        }
        return Math.min(askSpotLimit, 6);
    }
}
