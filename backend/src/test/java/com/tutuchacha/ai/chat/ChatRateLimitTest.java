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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.chat.rate-limit-per-minute=2")
class ChatRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiGateway aiGateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ChatRateLimiter rateLimiter;

    @BeforeEach
    void seedDemoUser() {
        rateLimiter.reset();
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = 'demo'", Integer.class);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) "
                    + "values('demo', 'x', 'demo', 'USER')");
        }
        when(aiGateway.complete(any(AiChatRequest.class)))
                .thenReturn(new AiChatResponse("好", "qwen-plus", 3, 1, 100));
    }

    @Test
    void thirdRequestInSameMinuteIsRejected() throws Exception {
        send("第一条");
        send("第二条");

        mockMvc.perform(post("/api/chat")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第三条\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("频繁")));
    }

    @Test
    void otherUserHasOwnWindow() throws Exception {
        send("第一条");
        send("第二条");

        mockMvc.perform(post("/api/chat")
                        .with(user("another").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"换个用户\"}"))
                .andExpect(status().isOk());
    }

    private void send(String content) throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk());
    }
}
