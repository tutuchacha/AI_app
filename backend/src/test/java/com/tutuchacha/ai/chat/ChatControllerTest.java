package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiGateway aiGateway;

    @Test
    void chatReturnsAnswerAndGeneratedConversationId() throws Exception {
        when(aiGateway.complete(any(AiChatRequest.class)))
                .thenReturn(new com.tutuchacha.ai.gateway.AiChatResponse("你好，我是星屿", "qwen-plus", 12, 7, 800));

        mockMvc.perform(post("/api/chat")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.conversationId").value(org.hamcrest.Matchers.startsWith("c_")))
                .andExpect(jsonPath("$.data.content").value("你好，我是星屿"))
                .andExpect(jsonPath("$.data.model").value("qwen-plus"))
                .andExpect(jsonPath("$.data.promptTokens").value(12))
                .andExpect(jsonPath("$.data.completionTokens").value(7));
    }

    @Test
    void keepsGivenConversationId() throws Exception {
        when(aiGateway.complete(any(AiChatRequest.class)))
                .thenReturn(new com.tutuchacha.ai.gateway.AiChatResponse("继续", "qwen-plus", 3, 2, 200));

        mockMvc.perform(post("/api/chat")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"再说一句\",\"conversationId\":\"c_existing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value("c_existing"));
    }

    @Test
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
