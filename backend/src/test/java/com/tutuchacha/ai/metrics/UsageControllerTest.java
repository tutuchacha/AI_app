package com.tutuchacha.ai.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用量看板：Prometheus 在测试里关闭，只验数据库口径的今日统计与归属。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private long demoUserId;
    private long otherUserId;

    @BeforeEach
    void seed() {
        demoUserId = seedUser("usage-demo");
        otherUserId = seedUser("usage-other");
    }

    private long seedUser(String username) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = ?", Integer.class, username);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) values(?, 'x', ?, 'USER')",
                    username, username);
        }
        Long id = jdbc.queryForObject("select id from sys_user where username = ?", Long.class, username);
        return id == null ? 0L : id;
    }

    private long seedConversation(long userId, String module) {
        jdbc.update("insert into conversation(uid, user_id, module, title) values(?, ?, ?, 't')",
                "c-" + System.nanoTime(), userId, module);
        Long id = jdbc.queryForObject("select max(id) from conversation where user_id = ?", Long.class, userId);
        return id == null ? 0L : id;
    }

    private void seedAssistant(long conversationId, int tokensIn, int tokensOut, int latencyMs,
                               String status, LocalDateTime createdAt) {
        jdbc.update("insert into message(conversation_id, role, content, tokens_in, tokens_out, latency_ms, "
                        + "model, status, created_at) values(?, 'assistant', 'x', ?, ?, ?, 'qwen-plus', ?, ?)",
                conversationId, tokensIn, tokensOut, latencyMs, status, createdAt);
    }

    @Test
    void aggregatesTodayUsageAndUserScope() throws Exception {
        long demoConversation = seedConversation(demoUserId, "chat");
        long otherConversation = seedConversation(otherUserId, "companion");
        LocalDateTime noon = LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0);

        seedAssistant(demoConversation, 100, 50, 1000, "DONE", noon);
        seedAssistant(demoConversation, 200, 80, 3000, "DONE", noon);
        seedAssistant(demoConversation, 10, 5, 9000, "FAILED", noon);
        seedAssistant(otherConversation, 40, 20, 2000, "DONE", noon);
        // 昨天的不进今日统计，但要进 7 天曲线
        seedAssistant(otherConversation, 70, 30, 1500, "DONE", noon.minusDays(1));

        MvcResult result = mockMvc.perform(get("/api/metrics/usage?days=7")
                        .with(user("usage-demo").roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");

        assertThat(data.path("source").asText()).isEqualTo("db");
        assertThat(data.path("prometheusUp").asBoolean()).isFalse();
        assertThat(data.path("daily")).hasSize(7);
        assertThat(data.path("daily").get(6).path("calls").asLong()).isGreaterThanOrEqualTo(4);

        JsonNode today = data.path("today");
        assertThat(today.path("calls").asLong()).isGreaterThanOrEqualTo(4);
        assertThat(today.path("tokensIn").asLong()).isGreaterThanOrEqualTo(350);
        assertThat(today.path("tokensOut").asLong()).isGreaterThanOrEqualTo(155);
        assertThat(today.path("errorRate").asDouble()).isGreaterThan(0);
        assertThat(today.path("p95Ms").asDouble()).isGreaterThan(0);

        JsonNode mine = data.path("mine");
        assertThat(mine.path("calls").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(mine.path("tokensIn").asLong()).isGreaterThanOrEqualTo(310);

        assertThat(data.path("modules").isArray()).isTrue();
        assertThat(data.path("modules").toString()).contains("通用对话").contains("恋爱陪伴");
        assertThat(data.toString()).doesNotContain("usage-demo");
    }
}
