package com.tutuchacha.ai.chat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M1-06 聊天指标：Prometheus 抓 /actuator/prometheus，Grafana 出图。
 * 只记业务语义（请求/结果/token/耗时），JVM 与 HTTP 指标由 Actuator 自带。
 */
@Component
public class ChatMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeStreams = new AtomicInteger();

    public ChatMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("ai_chat_stream_active", activeStreams, AtomicInteger::get)
                .description("In-flight SSE chat streams")
                .register(registry);
    }

    /** 正常结束：finishReason 为 stop / length。 */
    public void done(String module, String model, long latencyMs, int promptTokens, int completionTokens) {
        record(module, model, "done", latencyMs, promptTokens, completionTokens);
    }

    /** 客户端停止：已生成内容保留，status=stopped。 */
    public void stopped(String module, String model, long latencyMs, int promptTokens, int completionTokens) {
        record(module, model, "stopped", latencyMs, promptTokens, completionTokens);
    }

    /** 模型或内部异常。 */
    public void failed(String module, String model, long latencyMs) {
        record(module, model, "failed", latencyMs, 0, 0);
    }

    public void streamStarted() {
        activeStreams.incrementAndGet();
    }

    public void streamEnded() {
        activeStreams.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    private void record(String module, String model, String status, long latencyMs,
                        int promptTokens, int completionTokens) {
        String mod = StringUtils.hasText(module) ? module : "chat";
        String mdl = StringUtils.hasText(model) ? model : "unknown";

        registry.counter("ai_chat_requests", "module", mod, "model", mdl, "status", status).increment();
        if (promptTokens > 0) {
            registry.counter("ai_chat_tokens", "type", "input").increment(promptTokens);
        }
        if (completionTokens > 0) {
            registry.counter("ai_chat_tokens", "type", "output").increment(completionTokens);
        }
        Timer.builder("ai_chat_latency")
                .description("One chat turn, including model generation")
                .tags("module", mod, "model", mdl)
                .publishPercentiles(0.5, 0.95)
                // 分位数是 2 分钟滑动窗口，闲时会归零；桶是累计值，Grafana 才算得准
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofMillis(Math.max(latencyMs, 0)));
    }

    /** 便于测试断言：当前进行中的流数量。 */
    int activeStreams() {
        return activeStreams.get();
    }
}
