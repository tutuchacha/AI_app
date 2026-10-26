package com.tutuchacha.ai.chat.ws;

import com.tutuchacha.ai.auth.JwtService;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiGateway;
import com.tutuchacha.ai.gateway.AiStreamChunk;
import com.tutuchacha.ai.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private AiGateway aiGateway;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void seedDemoUser() {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = 'demo'", Integer.class);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) "
                    + "values('demo', 'x', 'demo', 'USER')");
        }
        Long id = jdbc.queryForObject(
                "select id from sys_user where username = 'demo'", Long.class);
        User user = new User();
        user.setId(id);
        user.setUsername("demo");
        user.setRole("USER");
        token = jwtService.issue(user);
    }

    @Test
    void streamsChatEventsOverWebSocket() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("你", "qwen-plus", null, null),
                new AiStreamChunk("好", "qwen-plus", 12, 2)));

        List<String> frames = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        WebSocketSession session = connect(frames, closed);

        session.sendMessage(new TextMessage(
                "{\"content\":\"你好\",\"module\":\"chat\",\"model\":\"qwen-plus\"}"));

        assertThat(closed.await(10, TimeUnit.SECONDS)).as("服务端应在 done 后关闭连接").isTrue();
        String all = String.join("\n", frames);
        assertThat(all).contains("\"type\":\"meta\"", "\"type\":\"delta\"",
                "\"type\":\"usage\"", "\"type\":\"done\"");
        assertThat(all).contains("\"conversationId\":\"c_", "\"content\":\"你\"", "\"content\":\"好\"");
        assertThat(all).doesNotContain("\"type\":\"error\"");
    }

    @Test
    void rejectsHandshakeWithoutToken() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHandler handler = new TextWebSocketHandler();

        assertThatThrownBy(() -> client
                .execute(handler, "ws://127.0.0.1:" + port + "/ws/chat")
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    private WebSocketSession connect(List<String> frames, CountDownLatch closed) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                frames.add(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closed.countDown();
            }
        };
        return client.execute(handler,
                        "ws://127.0.0.1:" + port + "/ws/chat?token=" + token)
                .get(5, TimeUnit.SECONDS);
    }
}
