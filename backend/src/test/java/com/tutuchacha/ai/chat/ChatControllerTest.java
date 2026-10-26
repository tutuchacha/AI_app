package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiGateway;
import com.tutuchacha.ai.gateway.AiMessage;
import com.tutuchacha.ai.gateway.AiStreamChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerTest {

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

    @Test
    void streamEmitsMetaDeltaUsageDoneInOrder() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("你", "qwen-plus", null, null),
                new AiStreamChunk("好", "qwen-plus", null, null),
                new AiStreamChunk("", "qwen-plus", 12, 2)));

        MvcResult pending = mockMvc.perform(post("/api/chat/stream")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("\"type\":\"meta\"", "\"type\":\"usage\"", "\"type\":\"done\"");
        assertThat(body).contains("\"conversationId\":\"c_", "\"model\":\"qwen-plus\"");
        assertThat(body).contains("\"content\":\"你\"", "\"content\":\"好\"");
        assertThat(body).contains("\"promptTokens\":12", "\"completionTokens\":2");
        assertThat(body).doesNotContain("\"type\":\"error\"");
        assertThat(body.indexOf("\"type\":\"meta\"")).isLessThan(body.indexOf("\"type\":\"delta\""));
        assertThat(body.lastIndexOf("\"type\":\"usage\"")).isLessThan(body.indexOf("\"type\":\"done\""));
    }

    @Test
    void streamTurnsFailureIntoErrorEvent() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "未配置模型密钥"));

        MvcResult pending = mockMvc.perform(post("/api/chat/stream")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("\"type\":\"meta\"", "\"type\":\"error\"", "MODEL_NOT_CONFIGURED");
        assertThat(body).doesNotContain("\"type\":\"done\"");
    }

    @Test
    void streamKeepsGivenConversationId() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.empty());

        MvcResult pending = mockMvc.perform(post("/api/chat/stream")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"继续\",\"conversationId\":\"c_existing\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("\"conversationId\":\"c_existing\"");
    }

    @Test
    void streamIncludesPreviousTurnsAsContext() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("记住了，你叫小林", "qwen-plus", 10, 5, "stop")));

        String first = streamBody("{\"content\":\"我叫小林\"}");
        String uid = conversationIdOf(first);

        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("你叫小林", "qwen-plus", 20, 5, "stop")));
        streamBody("{\"content\":\"我叫什么\",\"conversationId\":\"" + uid + "\"}");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway, times(2)).stream(captor.capture());
        List<AiMessage> second = captor.getAllValues().get(1).messages();

        assertThat(second).extracting(AiMessage::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(second.get(1).content()).isEqualTo("我叫小林");
        assertThat(second.get(2).content()).isEqualTo("记住了，你叫小林");
        assertThat(second.get(3).content()).isEqualTo("我叫什么");
    }

    @Test
    void doneCarriesFinishReasonAndMetaCarriesMessageId() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("半句", "qwen-plus", 12, 1024, "length")));

        String body = streamBody("{\"content\":\"写长一点\"}");

        assertThat(body).contains("\"finishReason\":\"length\"");
        assertThat(body).containsPattern("\"messageId\":\"\\d+\"");
    }

    @Test
    void retryRegeneratesWithoutDuplicatingUserMessage() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("第一版", "qwen-plus", 5, 3, "stop")));
        String uid = conversationIdOf(streamBody("{\"content\":\"写首诗\",\"module\":\"chat\"}"));

        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("第二版", "qwen-plus", 6, 4, "stop")));
        String body = streamBody("{\"content\":\"写首诗\",\"conversationId\":\"" + uid
                + "\",\"module\":\"chat\",\"retry\":true}");

        Integer userMessages = jdbc.queryForObject(
                "select count(*) from message m join conversation c on c.id = m.conversation_id "
                        + "where c.uid = ? and m.role = 'user'", Integer.class, uid);
        assertThat(userMessages).isEqualTo(1);
        assertThat(body).contains("\"content\":\"第二版\"");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway, times(2)).stream(captor.capture());
        List<AiMessage> retried = captor.getAllValues().get(1).messages();
        assertThat(retried).extracting(AiMessage::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(retried.get(3).content()).isEqualTo("写首诗");
    }

    @Test
    void retryAfterFailureDoesNotAppendQuestionTwice() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "上游不可用"));
        String uid = conversationIdOf(streamBody("{\"content\":\"讲个笑话\",\"module\":\"chat\"}"));

        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("补上了", "qwen-plus", 8, 3, "stop")));
        String body = streamBody("{\"content\":\"讲个笑话\",\"conversationId\":\"" + uid
                + "\",\"module\":\"chat\",\"retry\":true}");

        assertThat(body).contains("\"content\":\"补上了\"").doesNotContain("\"type\":\"error\"");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway, times(2)).stream(captor.capture());
        assertThat(captor.getAllValues().get(1).messages())
                .extracting(AiMessage::role)
                .containsExactly("system", "user");
    }

    private String streamBody(String json) throws Exception {
        MvcResult pending = mockMvc.perform(post("/api/chat/stream")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String conversationIdOf(String body) {
        Matcher matcher = Pattern.compile("\"conversationId\":\"(c_[a-z0-9]+)\"").matcher(body);
        assertThat(matcher.find()).as("meta 事件应包含会话 ID").isTrue();
        return matcher.group(1);
    }
}
