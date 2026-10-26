package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiGateway aiGateway;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedDemoUser() {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = 'demo'", Integer.class);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) "
                    + "values('demo', 'x', 'demo', 'USER')");
        }
    }

    @Test
    void chatTurnExposesPrometheusMetrics() throws Exception {
        when(aiGateway.complete(any(AiChatRequest.class)))
                .thenReturn(new AiChatResponse("你好", "qwen-plus", 12, 7, 640));

        mockMvc.perform(post("/api/chat")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"指标测试\",\"module\":\"chat\",\"model\":\"qwen-plus\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ai_chat_requests_total")))
                .andExpect(content().string(containsString("status=\"done\"")))
                .andExpect(content().string(containsString("model=\"qwen-plus\"")))
                .andExpect(content().string(containsString("module=\"chat\"")))
                .andExpect(content().string(containsString("ai_chat_tokens_total{type=\"input\"}")))
                .andExpect(content().string(containsString("ai_chat_tokens_total{type=\"output\"}")))
                .andExpect(content().string(containsString("ai_chat_latency_seconds_count")))
                .andExpect(content().string(containsString("ai_chat_stream_active")));
    }

    @Test
    void failedTurnIsCountedAsFailed() throws Exception {
        when(aiGateway.complete(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("upstream down"));

        mockMvc.perform(post("/api/chat")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"失败测试\",\"module\":\"chat\",\"model\":\"qwen-max\"}"))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("status=\"failed\"")))
                .andExpect(content().string(containsString("model=\"qwen-max\"")));
    }
}
