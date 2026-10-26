package com.tutuchacha.ai.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M1-08 聊天保护参数：默认每人每分钟 20 轮，0 表示不限流。
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(Integer rateLimitPerMinute) {

    public int rateLimitPerMinuteOrDefault() {
        return rateLimitPerMinute == null ? 20 : rateLimitPerMinute;
    }
}
