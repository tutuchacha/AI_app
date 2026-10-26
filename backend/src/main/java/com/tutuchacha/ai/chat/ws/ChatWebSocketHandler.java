package com.tutuchacha.ai.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.chat.ChatOrchestrator;
import com.tutuchacha.ai.chat.dto.ChatEvent;
import com.tutuchacha.ai.chat.dto.ChatRequest;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1-09 WS 持久连接：一次握手可跑多轮对话。
 * 客户端帧：{"type":"chat"|"stop"|"ping", ...}；服务端帧：ChatEvent 或 {"type":"pong"}。
 * stop 只取消当前轮，连接保留；连接关闭等价于 stop。
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    static final String USERNAME = "username";
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final int MAX_CONTENT = 4000;

    private final ChatOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final Map<String, Active> running = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 中文 4000 字约 12KB，默认 8KB 不够
        session.setTextMessageSizeLimit(64 * 1024);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode frame;
        try {
            frame = objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            sendError(session, "BAD_REQUEST", "请求格式不正确");
            return;
        }
        // 兼容 M1-07 的旧客户端：没有 type 的帧按 chat 处理
        String type = frame.path("type").asText("chat");
        switch (type) {
            case "chat" -> startRound(session, frame);
            case "stop" -> stopRound(session);
            case "ping" -> sendRaw(session, Map.of("type", "pong"));
            default -> sendError(session, "BAD_REQUEST", "未知帧类型：" + type);
        }
    }

    private void startRound(WebSocketSession session, JsonNode frame) {
        if (running.containsKey(session.getId())) {
            sendError(session, "BUSY", "上一轮还在生成，请稍候");
            return;
        }
        ChatRequest request;
        try {
            request = objectMapper.treeToValue(frame, ChatRequest.class);
        } catch (Exception ex) {
            sendError(session, "BAD_REQUEST", "请求格式不正确");
            return;
        }
        if (request.content() == null || request.content().isBlank()) {
            sendError(session, "BAD_REQUEST", "内容不能为空");
            return;
        }
        if (request.content().length() > MAX_CONTENT) {
            sendError(session, "BAD_REQUEST", "单条消息最长 4000 字");
            return;
        }

        String username = (String) session.getAttributes().get(USERNAME);
        Flux<ChatEvent> events;
        try {
            events = orchestrator.stream(username, request);
        } catch (BusinessException ex) {
            String code = ex.getErrorCode() == ErrorCode.TOO_MANY_REQUESTS ? "RATE_LIMITED" : "BAD_REQUEST";
            sendError(session, code, ex.getMessage());
            return;
        }

        Active active = new Active();
        running.put(session.getId(), active);
        try {
            active.subscription = events.subscribe(
                    event -> send(session, event),
                    ex -> {
                        log.warn("ws stream failed session={}: {}", session.getId(), ex.getMessage());
                        running.remove(session.getId(), active);
                        sendError(session, "INTERNAL", "生成失败，请稍后重试");
                    },
                    () -> running.remove(session.getId(), active));
        } catch (RuntimeException ex) {
            running.remove(session.getId());
            log.warn("ws subscribe failed session={}: {}", session.getId(), ex.getMessage());
            sendError(session, "INTERNAL", "生成失败，请稍后重试");
        }
    }

    /** 只停当前轮：取消上游会触发 ChatOrchestrator 的 doOnCancel -> STOPPED。 */
    private void stopRound(WebSocketSession session) {
        Active active = running.remove(session.getId());
        if (active != null) {
            active.dispose();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        stopRound(session);
    }

    private void sendError(WebSocketSession session, String code, String message) {
        send(session, ChatEvent.error(code, message));
    }

    private void send(WebSocketSession session, ChatEvent event) {
        if (!session.isOpen()) {
            stopRound(session);
            return;
        }
        try {
            // WebSocketSession 不是线程安全的，流式回调与 pong 可能并发
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
        } catch (Exception ex) {
            log.debug("ws send failed session={}: {}", session.getId(), ex.getMessage());
            stopRound(session);
            close(session);
        }
    }

    private void sendRaw(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception ex) {
            log.debug("ws send failed session={}: {}", session.getId(), ex.getMessage());
            close(session);
        }
    }

    private void close(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (Exception ex) {
            log.debug("ws close failed session={}: {}", session.getId(), ex.getMessage());
        }
    }

    private static final class Active {
        private volatile Disposable subscription;

        private void dispose() {
            Disposable current = subscription;
            if (current != null && !current.isDisposed()) {
                current.dispose();
            }
        }
    }
}
