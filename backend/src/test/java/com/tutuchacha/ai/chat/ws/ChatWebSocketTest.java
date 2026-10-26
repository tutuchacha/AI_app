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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

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
    void reusesOneConnectionForMultipleRounds() throws Exception {
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(
                Flux.just(new AiStreamChunk("第一轮", "qwen-plus", 5, 1)),
                Flux.just(new AiStreamChunk("第二轮", "qwen-plus", 6, 2)));

        List<String> frames = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        WebSocketSession session = connect(frames, closed);

        session.sendMessage(new TextMessage(chatFrame("问题一")));
        assertThat(await(() -> doneCount(frames) >= 1)).as("第一轮应收到 done").isTrue();

        session.sendMessage(new TextMessage(chatFrame("问题二")));
        assertThat(await(() -> doneCount(frames) >= 2)).as("第二轮应复用同一连接").isTrue();

        String all = String.join("\n", frames);
        assertThat(session.isOpen()).isTrue();
        assertThat(all).contains("第一轮", "第二轮");
        assertThat(count(all, "\"type\":\"meta\"")).isEqualTo(2);
        assertThat(count(all, "\"type\":\"done\"")).isEqualTo(2);
        assertThat(closed.getCount()).isEqualTo(1);
        session.close();
    }

    @Test
    void stopFrameCancelsRoundAndKeepsConnection() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(
                Flux.concat(
                        Flux.just(new AiStreamChunk("半截", "qwen-plus", 5, 1)),
                        Flux.<AiStreamChunk>never())
                        .doOnCancel(() -> cancelled.set(true)),
                Flux.just(new AiStreamChunk("完整", "qwen-plus", 6, 2)));

        List<String> frames = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        WebSocketSession session = connect(frames, closed);

        session.sendMessage(new TextMessage(chatFrame("写一半就停")));
        assertThat(await(() -> frames.stream().anyMatch(f -> f.contains("半截")))).isTrue();

        session.sendMessage(new TextMessage("{\"type\":\"stop\"}"));
        assertThat(await(cancelled::get)).as("stop 应取消上游").isTrue();
        assertThat(session.isOpen()).as("stop 不应关闭连接").isTrue();

        session.sendMessage(new TextMessage(chatFrame("停后再来一轮")));
        assertThat(await(() -> doneCount(frames) >= 1)).as("停后应能继续下一轮").isTrue();
        assertThat(String.join("\n", frames)).contains("完整");
        session.close();
    }

    @Test
    void answersPingWithoutClosing() throws Exception {
        List<String> frames = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        WebSocketSession session = connect(frames, closed);

        session.sendMessage(new TextMessage("{\"type\":\"ping\"}"));

        assertThat(await(() -> frames.stream().anyMatch(f -> f.contains("\"type\":\"pong\"")))).isTrue();
        assertThat(session.isOpen()).isTrue();
        session.close();
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

    private String chatFrame(String content) {
        return "{\"type\":\"chat\",\"content\":\"" + content + "\",\"module\":\"chat\"}";
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

    private boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    private long doneCount(List<String> frames) {
        return frames.stream().filter(f -> f.contains("\"type\":\"done\"")).count();
    }

    private int count(String text, String needle) {
        int total = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            total += 1;
            index += needle.length();
        }
        return total;
    }
}
