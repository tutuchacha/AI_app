package com.tutuchacha.ai.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void okCarriesDataAndTraceId() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        Result<String> result = Result.ok("hello");

        assertThat(result.code()).isZero();
        assertThat(result.message()).isEqualTo("成功");
        assertThat(result.data()).isEqualTo("hello");
        assertThat(result.traceId()).isEqualTo("trace-123");
    }

    @Test
    void failUsesErrorCodeAndKeepsTraceId() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-456");

        Result<Void> result = Result.fail(ErrorCode.BAD_REQUEST, "username 不能为空");

        assertThat(result.code()).isEqualTo(400);
        assertThat(result.message()).isEqualTo("username 不能为空");
        assertThat(result.data()).isNull();
        assertThat(result.traceId()).isEqualTo("trace-456");
    }

    @Test
    void okWithoutMdcHasNullTraceId() {
        assertThat(Result.ok().traceId()).isNull();
    }
}
