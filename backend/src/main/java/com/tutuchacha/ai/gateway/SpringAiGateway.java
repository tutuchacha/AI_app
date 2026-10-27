package com.tutuchacha.ai.gateway;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * AiGateway 的 Spring AI 实现。厂商相关代码只允许出现在这个类里。
 */
@Component
public class SpringAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(SpringAiGateway.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AiProperties properties;

    public SpringAiGateway(ObjectProvider<ChatModel> chatModelProvider,
                           ObjectProvider<EmbeddingModel> embeddingModelProvider,
                           AiProperties properties) {
        this.chatModelProvider = chatModelProvider;
        this.embeddingModelProvider = embeddingModelProvider;
        this.properties = properties;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingModel embeddingModel = requireEmbeddingModel();
        try {
            List<float[]> vectors = Mono.fromCallable(() -> embeddingModel.embed(texts))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(properties.timeoutOrDefault())
                    .onErrorMap(this::mapError)
                    .block();
            return vectors == null ? List.of() : vectors;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapError(ex);
        }
    }

    @Override
    public AiChatResponse complete(AiChatRequest request) {
        ChatModel chatModel = requireChatModel();
        long startedAt = System.currentTimeMillis();
        try {
            ChatResponse response = Mono.fromCallable(() ->
                            chatModel.call(new Prompt(toSpringMessages(request), buildOptions(request, false))))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(properties.timeoutOrDefault())
                    .onErrorMap(this::mapError)
                    .block();
            if (response == null) {
                throw new BusinessException(ErrorCode.BAD_GATEWAY, "模型没有返回内容，请稍后重试");
            }
            Usage usage = response.getMetadata().getUsage();
            return new AiChatResponse(
                    textOf(response),
                    modelOf(response, request),
                    usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                    usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                    System.currentTimeMillis() - startedAt);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapError(ex);
        }
    }

    @Override
    public Flux<AiStreamChunk> stream(AiChatRequest request) {
        ChatModel chatModel = requireChatModel();
        return chatModel.stream(new Prompt(toSpringMessages(request), buildOptions(request, true)))
                .timeout(properties.timeoutOrDefault())
                .map(response -> new AiStreamChunk(
                        textOf(response),
                        modelOf(response, request),
                        promptTokensOf(response),
                        completionTokensOf(response),
                        finishReasonOf(response)))
                .onErrorMap(this::mapError);
    }

    /** 统一把超时、上游限流和普通异常翻成可展示的业务错误。 */
    private BusinessException mapError(Throwable ex) {
        if (ex instanceof BusinessException business) {
            return business;
        }
        if (ex instanceof TimeoutException) {
            log.warn("模型调用超时 traceId={} timeout={}", MDC.get("traceId"), properties.timeoutOrDefault());
            return new BusinessException(ErrorCode.GATEWAY_TIMEOUT, "模型响应超时，请稍后重试");
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("429") || message.contains("rate limit") || message.contains("too many requests")) {
            log.warn("模型服务限流 traceId={}: {}", MDC.get("traceId"), ex.getMessage());
            return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "模型服务繁忙，请稍后重试");
        }
        log.error("模型调用失败 traceId={}", MDC.get("traceId"), ex);
        return new BusinessException(ErrorCode.BAD_GATEWAY, "模型调用失败，请稍后重试");
    }

    @Override
    public String defaultModel() {
        return properties.chatModelOrDefault();
    }

    private ChatModel requireChatModel() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "未配置模型密钥：请设置环境变量 DASHSCOPE_API_KEY 后重启后端");
        }
        return chatModel;
    }

    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "未配置模型密钥：请设置环境变量 DASHSCOPE_API_KEY 后重启后端");
        }
        return embeddingModel;
    }

    private List<Message> toSpringMessages(AiChatRequest request) {
        return request.messages().stream()
                .map(this::toSpringMessage)
                .toList();
    }

    private Message toSpringMessage(AiMessage message) {
        String role = message.role() == null ? "user" : message.role().toLowerCase();
        return switch (role) {
            case "system" -> new SystemMessage(message.content());
            case "assistant" -> new AssistantMessage(message.content());
            default -> new UserMessage(message.content());
        };
    }

    private OpenAiChatOptions buildOptions(AiChatRequest request, boolean streaming) {
        return OpenAiChatOptions.builder()
                .model(resolveModel(request))
                .temperature(request.temperature() == null ? properties.temperatureOrDefault() : request.temperature())
                .maxTokens(request.maxTokens() == null ? properties.maxTokensOrDefault() : request.maxTokens())
                .streamUsage(streaming)
                .build();
    }

    private String resolveModel(AiChatRequest request) {
        return StringUtils.hasText(request.model()) ? request.model() : properties.chatModelOrDefault();
    }

    private String modelOf(ChatResponse response, AiChatRequest request) {
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return StringUtils.hasText(model) ? model : resolveModel(request);
    }

    private Integer promptTokensOf(ChatResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return usage == null ? null : usage.getPromptTokens();
    }

    private Integer completionTokensOf(ChatResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return usage == null ? null : usage.getCompletionTokens();
    }

    private String finishReasonOf(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }

    private String textOf(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
