package com.tutuchacha.ai.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M3-05 用量看板配置：Prometheus 地址、开关与缓存时长。
 * 直连内网一会儿失败也不影响看板（自动退回数据库口径）。
 */
@ConfigurationProperties(prefix = "app.metrics")
public record MetricsProperties(
        String prometheusUrl,
        Boolean enabled,
        Integer cacheSeconds
) {

    public String prometheusUrlOrDefault() {
        return prometheusUrl == null || prometheusUrl.isBlank()
                ? "http://192.168.101.137:9090" : prometheusUrl.trim();
    }

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }

    /** 看板刷新很频繁，默认缓存 30 秒，避免每次点开都打 Prometheus。 */
    public int cacheSecondsOrDefault() {
        return cacheSeconds == null || cacheSeconds < 0 ? 30 : cacheSeconds;
    }
}
