package com.tutuchacha.ai.chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M1-08 单机固定窗口限流：同一用户每分钟最多 N 轮。
 * 只防误触与脚本刷接口；多实例部署要换 Redis（M6）。
 */
@Component
public class ChatRateLimiter {

    private static final long WINDOW_MS = 60_000;

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public ChatRateLimiter(ChatProperties properties) {
        this(properties.rateLimitPerMinuteOrDefault(), Clock.systemUTC());
    }

    ChatRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** limit <= 0 表示关闭限流。 */
    public boolean tryAcquire(String username) {
        if (limit <= 0) {
            return true;
        }
        long minute = clock.millis() / WINDOW_MS;
        String key = username == null || username.isBlank() ? "anonymous" : username;
        Window window = windows.compute(key,
                (ignored, old) -> old == null || old.minute != minute ? new Window(minute) : old);
        return window.count.incrementAndGet() <= limit;
    }

    public int limit() {
        return limit;
    }

    /** 测试或运维手动清零，正常请求不需要调用。 */
    public void reset() {
        windows.clear();
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
