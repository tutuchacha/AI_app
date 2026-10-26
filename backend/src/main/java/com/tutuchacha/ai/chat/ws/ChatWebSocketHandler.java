package com.tutuchacha.ai.chat.ws;

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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1-07 WS 传输：一条连接承载一轮对话，帧就是 ChatEvent JSON。
 * 客户端关闭连接即取消上游生成，和 SSE 断连语义一致（消息标 STOPPED）。
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    static final String USERNAME = "username";
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final int MAX_CONTENT = 4000;

    private final ChatOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final Map<String, Disposable> running = new ConcurrentHashMap<>();

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
        ChatRequest request;
        try {
            request = objectMapper.readValue(message.getPayload(), ChatRequest.class);
        } catch (Exception ex) {
            reject(session, "请求格式不正确");
            return;
        }
        if (request.content() == null || request.content().isBlank()) {
            reject(session, "内容不能为空");
            return;
        }
        if (request.content().length() > MAX_CONTENT) {
            reject(session, "单条消息最长 4000 字");
            return;
        }
        if (running.containsKey(session.getId())) {
            send(session, ChatEvent.error("BUSY", "上一轮还在生成，请稍候"));
            return;
        }

        String username = (String) session.getAttributes().get(USERNAME);
        reactor.core.publisher.Flux<ChatEvent> events;
        try {
            events = orchestrator.stream(username, request);
        } catch (BusinessException ex) {
            String code = ex.getErrorCode() == ErrorCode.TOO_MANY_REQUESTS ? "RATE_LIMITED" : "BAD_REQUEST";
            send(session, ChatEvent.error(code, ex.getMessage()));
            close(session);
            return;
        }
        Disposable subscription = events
                .subscribe(
                        event -> send(session, event),
                        ex -> {
                            log.warn("ws stream failed session={}: {}", session.getId(), ex.getMessage());
                            send(session, ChatEvent.error("INTERNAL", "生成失败，请稍后重试"));
                            close(session);
                        },
                        () -> close(session));
        if (subscription.isDisposed()) {
            close(session);
        } else {
            running.put(session.getId(), subscription);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Disposable subscription = running.remove(session.getId());
        if (subscription != null) {
            // 取消上游，触发 ChatOrchestrator 的 doOnCancel -> STOPPED
            subscription.dispose();
        }
    }

    private void reject(WebSocketSession session, String message) {
        send(session, ChatEvent.error("BAD_REQUEST", message));
        close(session);
    }

    private void send(WebSocketSession session, ChatEvent event) {
        if (!session.isOpen()) {
            cancel(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (Exception ex) {
            log.debug("ws send failed session={}: {}", session.getId(), ex.getMessage());
            cancel(session);
        }
    }

    private void cancel(WebSocketSession session) {
        Disposable subscription = running.remove(session.getId());
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private void close(WebSocketSession session) {
        running.remove(session.getId());
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (Exception ex) {
            log.debug("ws close failed session={}: {}", session.getId(), ex.getMessage());
        }
    }
}
