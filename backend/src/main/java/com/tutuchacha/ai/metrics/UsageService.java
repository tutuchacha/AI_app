package com.tutuchacha.ai.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.tutuchacha.ai.user.User;
import com.tutuchacha.ai.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M3-05 用量口径：正文与 token 走 PG（精确、可追溯到人），
 * 活跃流等运行时指标走 Prometheus（Prometheus 挂了自动降级，不让看板 500）。
 */
@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private static final int MAX_DAYS = 30;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final String ACTIVE_STREAMS = "ai_chat_stream_active";

    /** 库里 module 是英文枚举，前端只认中文。 */
    private static final Map<String, String> MODULE_LABELS = Map.of(
            "chat", "通用对话",
            "companion", "恋爱陪伴",
            "support", "智能客服",
            "knowledge", "知识库",
            "analysis", "文档分析");

    private final UsageMapper usageMapper;
    private final UserMapper userMapper;
    private final MetricsProperties properties;
    private final RestClient prometheus;
    private volatile Snapshot cache = new Snapshot(null, -1, null, Instant.EPOCH);

    public UsageService(UsageMapper usageMapper, UserMapper userMapper, MetricsProperties properties) {
        this.usageMapper = usageMapper;
        this.userMapper = userMapper;
        this.properties = properties;
        this.prometheus = buildClient(properties);
    }

    public UsageView usage(String username, int days) {
        int span = Math.min(Math.max(days, 1), MAX_DAYS);
        Snapshot cached = cache;
        if (cached.view() != null && cached.days() == span
                && java.util.Objects.equals(cached.username(), username)
                && cached.expiresAt().isAfter(Instant.now())) {
            return cached.view();
        }
        UsageView view = load(username, span);
        cache = new Snapshot(view, span, username,
                Instant.now().plusSeconds(properties.cacheSecondsOrDefault()));
        return view;
    }

    private UsageView load(String username, int span) {
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(span - 1L).atStartOfDay();
        List<UsageRow> rows = usageMapper.selectSince(since);

        User user = findUser(username);
        List<UsageRow> mineRows = user == null ? List.of() : usageMapper.selectSinceForUser(user.getId(), since);

        PromInfo prom = readPrometheus();
        return new UsageView(
                prom.up() ? "prometheus+db" : "db",
                prom.up(),
                prom.activeStreams(),
                LocalDateTime.now(),
                totals(rows, today.atStartOfDay()),
                totals(mineRows, today.atStartOfDay()),
                daily(rows, today, span),
                modules(rows));
    }

    /** 只统计当天；latency 为 0 的旧数据不参与 P95，避免把分位数拉低。 */
    private UsageView.Totals totals(List<UsageRow> rows, LocalDateTime from) {
        long calls = 0;
        long tokensIn = 0;
        long tokensOut = 0;
        long failed = 0;
        List<Integer> latencies = new ArrayList<>(rows.size());
        for (UsageRow row : rows) {
            if (row.createdAt() == null || row.createdAt().isBefore(from)) {
                continue;
            }
            calls += 1;
            tokensIn += value(row.tokensIn());
            tokensOut += value(row.tokensOut());
            if ("FAILED".equalsIgnoreCase(row.status())) {
                failed += 1;
            }
            if (row.latencyMs() != null && row.latencyMs() > 0) {
                latencies.add(row.latencyMs());
            }
        }
        double p95 = percentile(latencies, 0.95);
        double errorRate = calls == 0 ? 0d : round(failed * 100d / calls, 2);
        return new UsageView.Totals(calls, tokensIn, tokensOut, round(p95, 0), errorRate);
    }

    private List<UsageView.DailyPoint> daily(List<UsageRow> rows, LocalDate today, int span) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (int i = span - 1; i >= 0; i -= 1) {
            counts.put(today.minusDays(i), 0L);
        }
        for (UsageRow row : rows) {
            if (row.createdAt() == null) {
                continue;
            }
            LocalDate day = row.createdAt().toLocalDate();
            counts.computeIfPresent(day, (key, value) -> value + 1);
        }
        List<UsageView.DailyPoint> points = new ArrayList<>(counts.size());
        counts.forEach((day, calls) ->
                points.add(new UsageView.DailyPoint(day.toString(), day.format(LABEL), calls)));
        return points;
    }

    private List<UsageView.ModuleShare> modules(List<UsageRow> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (UsageRow row : rows) {
            String key = StringUtils.hasText(row.module()) ? row.module().trim() : "chat";
            counts.merge(key, 1L, Long::sum);
        }
        long total = rows.size();
        List<UsageView.ModuleShare> shares = new ArrayList<>(counts.size());
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> shares.add(new UsageView.ModuleShare(
                        entry.getKey(),
                        MODULE_LABELS.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue(),
                        total == 0 ? 0d : round(entry.getValue() * 100d / total, 1))));
        return shares;
    }

    /** Prometheus 拿不到的只是活跃流数量，超时 1.5s 就放弃，别拖慢看板。 */
    private PromInfo readPrometheus() {
        if (!properties.enabledOrDefault()) {
            return PromInfo.DOWN;
        }
        try {
            JsonNode body = prometheus.get()
                    .uri(builder -> builder.path("/api/v1/query")
                            .queryParam("query", ACTIVE_STREAMS).build())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !"success".equals(body.path("status").asText())) {
                return PromInfo.DOWN;
            }
            double active = 0;
            for (JsonNode item : body.path("data").path("result")) {
                active += item.path("value").path(1).asDouble(0);
            }
            return new PromInfo(true, (int) Math.round(active));
        } catch (RuntimeException ex) {
            log.debug("Prometheus 不可用，用量看板退回数据库口径: {}", ex.getMessage());
            return PromInfo.DOWN;
        }
    }

    private RestClient buildClient(MetricsProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(1500);
        return RestClient.builder()
                .baseUrl(props.prometheusUrlOrDefault())
                .requestFactory(factory)
                .build();
    }

    private User findUser(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
    }

    private static long value(Integer raw) {
        return raw == null ? 0L : raw;
    }

    private static double percentile(List<Integer> values, double ratio) {
        if (values.isEmpty()) {
            return 0d;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int index = (int) Math.ceil(ratio * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private record PromInfo(boolean up, int activeStreams) {
        static final PromInfo DOWN = new PromInfo(false, 0);
    }

    private record Snapshot(UsageView view, int days, String username, Instant expiresAt) {
    }
}
