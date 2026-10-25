package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.chat.dto.ChatEvent;
import com.tutuchacha.ai.chat.dto.ChatRequest;
import com.tutuchacha.ai.chat.dto.ChatResult;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
import com.tutuchacha.ai.gateway.AiStreamChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聊天唯一业务入口：拼提示词、调模型、产出统一事件流。
 * M1-03 只做流式输出；会话落库在 M1-05 接进来。
 */
@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    static final String SYSTEM_PROMPT = """
            你是「星屿 AI」的助手，风格温和、简洁。
            默认使用简体中文回答；不确定的内容要说明不确定，不要编造。
            """;

    private final AiGateway aiGateway;

    public ChatOrchestrator(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    public ChatResult chat(String username, ChatRequest request) {
        String conversationId = conversationId(request);
        AiChatResponse response = aiGateway.complete(AiChatRequest.of(SYSTEM_PROMPT, request.content().trim()));

        log.info("chat user={} conversation={} model={} latencyMs={} tokens={}/{}",
                username, conversationId, response.model(), response.latencyMs(),
                response.promptTokens(), response.completionTokens());

        return new ChatResult(conversationId, response.content(), response.model(),
                response.promptTokens(), response.completionTokens(), response.latencyMs());
    }

    /**
     * 事件顺序：meta → delta* → usage → done；出错时为 meta → error。
     */
    public Flux<ChatEvent> stream(String username, ChatRequest request) {
        String conversationId = conversationId(request);
        String requestId = "r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String model = StringUtils.hasText(request.model()) ? request.model() : aiGateway.defaultModel();
        long startedAt = System.currentTimeMillis();
        AtomicInteger promptTokens = new AtomicInteger();
        AtomicInteger completionTokens = new AtomicInteger();

        Flux<ChatEvent> body = Flux.defer(() ->
                        aiGateway.stream(AiChatRequest.of(SYSTEM_PROMPT, request.content().trim())))
                .<ChatEvent>handle((chunk, sink) -> {
                    captureUsage(chunk, promptTokens, completionTokens);
                    if (StringUtils.hasText(chunk.content())) {
                        sink.next(ChatEvent.delta(chunk.content()));
                    }
                })
                .concatWith(Flux.defer(() -> {
                    long latencyMs = System.currentTimeMillis() - startedAt;
                    log.info("stream done user={} conversation={} model={} latencyMs={} tokens={}/{}",
                            username, conversationId, model, latencyMs,
                            promptTokens.get(), completionTokens.get());
                    return Flux.just(
                            ChatEvent.usage(promptTokens.get(), completionTokens.get()),
                            ChatEvent.done("stop", latencyMs));
                }))
                .onErrorResume(ex -> Flux.just(errorOf(ex)))
                .doOnCancel(() -> log.info("stream cancelled user={} conversation={}", username, conversationId));

        return Flux.just(ChatEvent.meta(conversationId, requestId, model)).concatWith(body);
    }

    private void captureUsage(AiStreamChunk chunk, AtomicInteger promptTokens, AtomicInteger completionTokens) {
        if (chunk.promptTokens() != null) {
            promptTokens.set(chunk.promptTokens());
        }
        if (chunk.completionTokens() != null) {
            completionTokens.set(chunk.completionTokens());
        }
    }

    private ChatEvent errorOf(Throwable ex) {
        if (ex instanceof BusinessException business) {
            String code = switch (business.getErrorCode()) {
                case SERVICE_UNAVAILABLE -> "MODEL_NOT_CONFIGURED";
                case BAD_GATEWAY -> "MODEL_ERROR";
                default -> "INTERNAL_ERROR";
            };
            return ChatEvent.error(code, business.getMessage());
        }
        log.error("流式对话异常 traceId={}", MDC.get("traceId"), ex);
        return ChatEvent.error("INTERNAL_ERROR", "服务开小差了，请稍后重试");
    }

    private String conversationId(ChatRequest request) {
        return StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
