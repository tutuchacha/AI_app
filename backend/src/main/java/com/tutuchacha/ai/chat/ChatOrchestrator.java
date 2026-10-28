package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.chat.dto.ChatEvent;
import com.tutuchacha.ai.chat.dto.ChatRequest;
import com.tutuchacha.ai.chat.dto.ChatResult;
import com.tutuchacha.ai.chat.dto.Citation;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
import com.tutuchacha.ai.gateway.AiMessage;
import com.tutuchacha.ai.gateway.AiStreamChunk;
import com.tutuchacha.ai.knowledge.KnowledgeBaseService;
import com.tutuchacha.ai.knowledge.RetrievalService;
import com.tutuchacha.ai.knowledge.dto.SearchHit;
import com.tutuchacha.ai.knowledge.dto.SearchRequest;
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

    /** 带知识库资料的系统提示：只依据资料回答，资料不足就明说。 */
    static final String RAG_SYSTEM_PROMPT = """
            你是「星屿 AI」的助手，风格温和、简洁。
            请严格依据下面给出的「参考资料」回答用户问题，不要用资料之外的信息补充事实。
            资料里没有答案时，直接说明「资料中没有相关信息」，不要编造。
            默认使用简体中文回答，并保持与资料内容一致。
            结合上文理解用户的追问，不要重复询问已经说过的信息。

            参考资料：
            %s
            """;

    /** 客服模式 + 没检索到资料：不猜，直接引导转人工。 */
    static final String SUPPORT_EMPTY_PROMPT = """
            你是「星屿 AI」的在线客服，礼貌、简短。
            本轮没有检索到任何售后资料，禁止编造政策、金额、时效或流程。
            请用一两句话说明暂时没有查到相关规定，并建议用户转人工客服继续处理。
            默认使用简体中文，不要提及「资料」「检索」这类内部词。
            """;

    /** 客服模式 + 有资料：只答资料内的，超出范围就转人工。 */
    static final String SUPPORT_RAG_PROMPT = """
            你是「星屿 AI」的在线客服，礼貌、简短。
            只能依据下面给出的「参考资料」回答，不要用资料之外的信息补充事实，
            也不要承诺资料里没有写明的时效与金额。
            资料没有覆盖的内容，直接说明「资料里没有这项说明」，并建议用户转人工客服。
            回答控制在 4 行以内，必要时分点，默认使用简体中文。

            参考资料：
            %s
            """;

    private static final int MAX_PROMPT_SNIPPET = 500;
    private static final int MAX_CITATION_SNIPPET = 120;

    private final AiGateway aiGateway;
    private final ConversationService conversationService;
    private final ChatMetrics metrics;
    private final ChatRateLimiter rateLimiter;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RetrievalService retrievalService;

    public ChatOrchestrator(AiGateway aiGateway, ConversationService conversationService,
                            ChatMetrics metrics, ChatRateLimiter rateLimiter,
                            KnowledgeBaseService knowledgeBaseService,
                            RetrievalService retrievalService) {
        this.aiGateway = aiGateway;
        this.conversationService = conversationService;
        this.metrics = metrics;
        this.rateLimiter = rateLimiter;
        this.knowledgeBaseService = knowledgeBaseService;
        this.retrievalService = retrievalService;
    }

    public ChatResult chat(String username, ChatRequest request) {
        checkRate(username);
        String content = request.content().trim();
        String model = resolveModel(request);
        String requestId = newRequestId();
        boolean retry = Boolean.TRUE.equals(request.retry());
        Long kbId = requireKb(username, request.kbId());

        ConversationService.Context context = conversationService.prepare(
                username, request.conversationId(), request.module(), model, content, requestId, retry, kbId);
        RagContext rag = retrieveContext(username, context.kbId(), content, request.module());
        List<Citation> citations = rag.citations();

        AiChatResponse response;
        long startedAt = System.currentTimeMillis();
        try {
            response = aiGateway.complete(new AiChatRequest(
                    model, messagesOf(context, content, retry, rag, request.module()), null, null));
        } catch (RuntimeException ex) {
            conversationService.finish(context, new ConversationService.Finish(
                    "", 0, 0, 0, "FAILED", citations));
            metrics.failed(request.module(), model, System.currentTimeMillis() - startedAt);
            throw ex;
        }

        conversationService.finish(context, new ConversationService.Finish(
                response.content(), response.promptTokens(), response.completionTokens(),
                response.latencyMs(), "DONE", citations));
        metrics.done(request.module(), model, response.latencyMs(),
                response.promptTokens(), response.completionTokens());

        log.info("chat user={} conversation={} model={} kb={} citations={} latencyMs={} tokens={}/{}",
                username, context.uid(), response.model(), context.kbId(), citations.size(),
                response.latencyMs(), response.promptTokens(), response.completionTokens());

        return new ChatResult(context.uid(), response.content(), response.model(),
                response.promptTokens(), response.completionTokens(), response.latencyMs(), citations);
    }

    /**
     * 事件顺序：meta → citation* → delta* → usage → done；出错时为 error（已有 meta 时 meta 在前）。
     */
    public Flux<ChatEvent> stream(String username, ChatRequest request) {
        checkRate(username);
        String content = request.content().trim();
        String model = resolveModel(request);
        String requestId = newRequestId();
        long startedAt = System.currentTimeMillis();
        boolean retry = Boolean.TRUE.equals(request.retry());

        ConversationService.Context context;
        try {
            // 显式指定的库先校验归属，避免先落库再报 403/404 留下脏消息
            Long kbId = requireKb(username, request.kbId());
            context = conversationService.prepare(
                    username, request.conversationId(), request.module(), model, content,
                    requestId, retry, kbId);
        } catch (BusinessException ex) {
            return Flux.just(errorOf(ex));
        }

        RagContext rag = retrieveContext(username, context.kbId(), content, request.module());
        List<Citation> citations = rag.citations();
        Flux<ChatEvent> citationEvents = Flux.fromIterable(citations).map(ChatEvent::citation);

        String conversationId = context.uid();
        AiChatRequest aiRequest = new AiChatRequest(
                model, messagesOf(context, content, retry, rag, request.module()), null, null);
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
                            answer.toString(), promptTokens.get(), completionTokens.get(), latencyMs,
                            "DONE", citations));
                    metrics.done(request.module(), model, latencyMs,
                            promptTokens.get(), completionTokens.get());
                    log.info("stream done user={} conversation={} model={} kb={} citations={} latencyMs={} tokens={}/{} finish={}",
                            username, conversationId, model, context.kbId(), citations.size(), latencyMs,
                            promptTokens.get(), completionTokens.get(), finishReason.get());
                    return Flux.just(
                            ChatEvent.usage(promptTokens.get(), completionTokens.get()),
                            ChatEvent.done(finishReason.get(), latencyMs));
                }))
                .onErrorResume(ex -> {
                    long latencyMs = System.currentTimeMillis() - startedAt;
                    conversationService.finish(context, new ConversationService.Finish(
                            answer.toString(), promptTokens.get(), completionTokens.get(),
                            latencyMs, "FAILED", citations));
                    metrics.failed(request.module(), model, latencyMs);
                    return Flux.just(errorOf(ex));
                })
                .doOnCancel(() -> {
                    long latencyMs = System.currentTimeMillis() - startedAt;
                    conversationService.finish(context, new ConversationService.Finish(
                            answer.toString(), promptTokens.get(), completionTokens.get(),
                            latencyMs, "STOPPED", citations));
                    metrics.stopped(request.module(), model, latencyMs,
                            promptTokens.get(), completionTokens.get());
                    log.info("stream cancelled user={} conversation={}", username, conversationId);
                });

        String messageId = context.assistantMessageId() == null
                ? null : context.assistantMessageId().toString();
        return Flux.just(ChatEvent.meta(conversationId, requestId, model, messageId))
                .concatWith(citationEvents)
                .concatWith(body)
                .doOnSubscribe(subscription -> metrics.streamStarted())
                .doFinally(signal -> metrics.streamEnded());
    }

    /** system + 历史 + 本轮 user；重试时若历史末尾已是同一条提问，就不再重复追加。 */
    private List<AiMessage> messagesOf(ConversationService.Context context, String content,
                                       boolean retry, RagContext rag, String module) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(systemPrompt(module, rag)));
        messages.addAll(context.history());
        boolean alreadyAsked = retry && !context.history().isEmpty()
                && "user".equals(lastRole(context.history()))
                && content.equals(lastContent(context.history()));
        if (!alreadyAsked) {
            messages.add(AiMessage.user(content));
        }
        return messages;
    }

    /** 客服模式的提示词更严：没资料就转人工，有资料也不许外推。 */
    private String systemPrompt(String module, RagContext rag) {
        if ("support".equals(module)) {
            return rag.prompt().isEmpty()
                    ? SUPPORT_EMPTY_PROMPT
                    : SUPPORT_RAG_PROMPT.formatted(rag.prompt());
        }
        return rag.prompt().isEmpty() ? SYSTEM_PROMPT : RAG_SYSTEM_PROMPT.formatted(rag.prompt());
    }

    /**
     * 检索当前库的片段：显式传入的库已在前面校验过归属；
     * 检索或向量服务异常时降级为无引用回答，不阻断聊天。
     */
    private RagContext retrieveContext(String username, Long kbId, String query, String module) {
        if (kbId == null) {
            return RagContext.EMPTY;
        }
        try {
            List<SearchHit> hits = retrievalService.search(username, kbId,
                    new SearchRequest(query, null, null));
            if (hits.isEmpty()) {
                return RagContext.EMPTY;
            }
            List<Citation> citations = hits.stream().map(this::toCitation).toList();
            StringBuilder materials = new StringBuilder();
            for (int i = 0; i < hits.size(); i += 1) {
                SearchHit hit = hits.get(i);
                materials.append('[').append(i + 1).append("] ")
                        .append(hit.filename() == null ? "" : hit.filename()).append('\n')
                        .append(truncate(hit.content(), MAX_PROMPT_SNIPPET)).append("\n\n");
            }
            return new RagContext(citations, materials.toString().trim());
        } catch (RuntimeException ex) {
            log.warn("RAG 检索降级 user={} kb={} module={}: {}", username, kbId, module, ex.getMessage());
            return RagContext.EMPTY;
        }
    }

    private Citation toCitation(SearchHit hit) {
        double score = hit.score() == null ? 0d : Math.round(hit.score() * 10000d) / 10000d;
        return new Citation(String.valueOf(hit.chunkId()),
                hit.filename() == null ? "" : hit.filename(),
                truncate(hit.content(), MAX_CITATION_SNIPPET), score);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "…";
    }

    private Long requireKb(String username, Long kbId) {
        if (kbId == null) {
            return null;
        }
        return knowledgeBaseService.requireOwned(username, kbId).getId();
    }

    /** 本轮检索结果：事件/落库用 citations，提示词用 prompt（可为空表示无资料）。 */
    private record RagContext(List<Citation> citations, String prompt) {

        private static final RagContext EMPTY = new RagContext(List.of(), "");
    }

    private String lastRole(List<AiMessage> history) {
        return history.get(history.size() - 1).role();
    }

    private String lastContent(List<AiMessage> history) {
        return history.get(history.size() - 1).content();
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

    private void checkRate(String username) {
        if (!rateLimiter.tryAcquire(username)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请 1 分钟后再试");
        }
    }

    private ChatEvent errorOf(Throwable ex) {
        if (ex instanceof BusinessException business) {
            String code = switch (business.getErrorCode()) {
                case SERVICE_UNAVAILABLE -> "MODEL_NOT_CONFIGURED";
                case BAD_GATEWAY -> "MODEL_ERROR";
                case GATEWAY_TIMEOUT -> "LLM_TIMEOUT";
                case TOO_MANY_REQUESTS -> "RATE_LIMITED";
                case FORBIDDEN -> "FORBIDDEN";
                case NOT_FOUND -> "KB_NOT_FOUND";
                default -> "INTERNAL_ERROR";
            };
            return ChatEvent.error(code, business.getMessage());
        }
        log.error("流式对话异常 traceId={}", MDC.get("traceId"), ex);
        return ChatEvent.error("INTERNAL_ERROR", "服务开小差了，请稍后重试");
    }
}
