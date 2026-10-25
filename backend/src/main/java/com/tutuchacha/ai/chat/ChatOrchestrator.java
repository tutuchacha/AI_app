package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.chat.dto.ChatEvent;
import com.tutuchacha.ai.chat.dto.ChatRequest;
import com.tutuchacha.ai.chat.dto.ChatResult;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
import com.tutuchacha.ai.gateway.AiMessage;
import com.tutuchacha.ai.gateway.AiStreamChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天唯一业务入口：取历史、拼提示词、调模型、产出统一事件流。
 * M1-05 起每轮都落库，模型能看到同一会话的上下文。
 */
@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    static final String SYSTEM_PROMPT = """
            你是「星屿 AI」的助手，风格温和、简洁。
            默认使用简体中文回答；不确定的内容要说明不确定，不要编造。
            结合上文理解用户的追问，不要重复询问已经说过的信息。
            """;

    private final AiGateway aiGateway;
    private final ConversationService conversationService;

    public ChatOrchestrator(AiGateway aiGateway, ConversationService conversationService) {
        this.aiGateway = aiGateway;
        this.conversationService = conversationService;
    }

    public ChatResult chat(String username, ChatRequest request) {
        String content = request.content().trim();
        String model = resolveModel(request);
        String requestId = newRequestId();
        ConversationService.Context context = conversationService.prepare(
                username, request.conversationId(), request.module(), model, content, requestId);

        AiChatResponse response;
        try {
            response = aiGateway.complete(new AiChatRequest(model, messagesOf(context, content), null, null));
        } catch (RuntimeException ex) {
            conversationService.finish(context, new ConversationService.Finish("", 0, 0, 0, "FAILED"));
            throw ex;
        }

        conversationService.finish(context, new ConversationService.Finish(
                response.content(), response.promptTokens(), response.completionTokens(),
                response.latencyMs(), "DONE"));

        log.info("chat user={} conversation={} model={} latencyMs={} tokens={}/{}",
                username, context.uid(), response.model(), response.latencyMs(),
                response.promptTokens(), response.completionTokens());

        return new ChatResult(context.uid(), response.content(), response.model(),
                response.promptTokens(), response.completionTokens(), response.latencyMs());
    }

    /**
     * 事件顺序：meta → delta* → usage → done；出错时为 error（已有 meta 时 meta 在前）。
     */
    public Flux<ChatEvent> stream(String username, ChatRequest request) {
        String content = request.content().trim();
        String model = resolveModel(request);
        String requestId = newRequestId();
        long startedAt = System.currentTimeMillis();

        ConversationService.Context context;
        try {
            context = conversationService.prepare(
                    username, request.conversationId(), request.module(), model, content, requestId);
        } catch (BusinessException ex) {
            return Flux.just(errorOf(ex));
        }

        String conversationId = context.uid();
        AiChatRequest aiRequest = new AiChatRequest(model, messagesOf(context, content), null, null);
        AtomicInteger promptTokens = new AtomicInteger();
        AtomicInteger completionTokens = new AtomicInteger();
        AtomicReference<String> finishReason = new AtomicReference<>("stop");
        StringBuilder answer = new StringBuilder();

        Flux<ChatEvent> body = Flux.defer(() -> aiGateway.stream(aiRequest))
                .<ChatEvent>handle((chunk, sink) -> {
                    captureUsage(chunk, promptTokens, completionTokens);
                    if (StringUtils.hasText(chunk.finishReason())) {
                        finishReason.set(chunk.finishReason().toLowerCase(Locale.ROOT));
                    }
                    if (StringUtils.hasText(chunk.content())) {
                        answer.append(chunk.content());
                        sink.next(ChatEvent.delta(chunk.content()));
                    }
                })
                .concatWith(Flux.defer(() -> {
                    long latencyMs = System.currentTimeMillis() - startedAt;
                    conversationService.finish(context, new ConversationService.Finish(
                            answer.toString(), promptTokens.get(), completionTokens.get(), latencyMs, "DONE"));
                    log.info("stream done user={} conversation={} model={} latencyMs={} tokens={}/{} finish={}",
                            username, conversationId, model, latencyMs,
                            promptTokens.get(), completionTokens.get(), finishReason.get());
                    return Flux.just(
                            ChatEvent.usage(promptTokens.get(), completionTokens.get()),
                            ChatEvent.done(finishReason.get(), latencyMs));
                }))
                .onErrorResume(ex -> {
                    conversationService.finish(context, new ConversationService.Finish(
                            answer.toString(), promptTokens.get(), completionTokens.get(),
                            System.currentTimeMillis() - startedAt, "FAILED"));
                    return Flux.just(errorOf(ex));
                })
                .doOnCancel(() -> {
                    conversationService.finish(context, new ConversationService.Finish(
                            answer.toString(), promptTokens.get(), completionTokens.get(),
                            System.currentTimeMillis() - startedAt, "STOPPED"));
                    log.info("stream cancelled user={} conversation={}", username, conversationId);
                });

        String messageId = context.assistantMessageId() == null
                ? null : context.assistantMessageId().toString();
        return Flux.just(ChatEvent.meta(conversationId, requestId, model, messageId)).concatWith(body);
    }

    /** system + 历史 + 本轮 user，历史为空时等价于单轮。 */
    private List<AiMessage> messagesOf(ConversationService.Context context, String content) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(SYSTEM_PROMPT));
        messages.addAll(context.history());
        messages.add(AiMessage.user(content));
        return messages;
    }

    private String resolveModel(ChatRequest request) {
        return StringUtils.hasText(request.model()) ? request.model() : aiGateway.defaultModel();
    }

    private String newRequestId() {
        return "r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
                case FORBIDDEN -> "FORBIDDEN";
                default -> "INTERNAL_ERROR";
            };
            return ChatEvent.error(code, business.getMessage());
        }
        log.error("流式对话异常 traceId={}", MDC.get("traceId"), ex);
        return ChatEvent.error("INTERNAL_ERROR", "服务开小差了，请稍后重试");
    }
}
