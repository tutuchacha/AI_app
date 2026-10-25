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
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AiGateway 的 Spring AI 实现。厂商相关代码只允许出现在这个类里。
 */
@Component
public class SpringAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(SpringAiGateway.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiProperties properties;

    public SpringAiGateway(ObjectProvider<ChatModel> chatModelProvider, AiProperties properties) {
        this.chatModelProvider = chatModelProvider;
        this.properties = properties;
    }

    @Override
    public AiChatResponse complete(AiChatRequest request) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "未配置模型密钥：请设置环境变量 DASHSCOPE_API_KEY 后重启后端");
        }
        if (request.messages().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息不能为空");
        }

        long startedAt = System.currentTimeMillis();
        try {
            ChatResponse response = chatModel.call(new Prompt(toSpringMessages(request), buildOptions(request)));
            Usage usage = response.getMetadata().getUsage();
            String model = StringUtils.hasText(response.getMetadata().getModel())
                    ? response.getMetadata().getModel()
                    : resolveModel(request);

            return new AiChatResponse(
                    textOf(response),
                    model,
                    usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                    usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                    System.currentTimeMillis() - startedAt);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("模型调用失败 traceId={} model={}", MDC.get("traceId"), resolveModel(request), ex);
            throw new BusinessException(ErrorCode.BAD_GATEWAY, "模型调用失败，请稍后重试");
        }
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

    private OpenAiChatOptions buildOptions(AiChatRequest request) {
        return OpenAiChatOptions.builder()
                .model(resolveModel(request))
                .temperature(request.temperature() == null ? properties.temperatureOrDefault() : request.temperature())
                .maxTokens(request.maxTokens() == null ? properties.maxTokensOrDefault() : request.maxTokens())
                .build();
    }

    private String resolveModel(AiChatRequest request) {
        return StringUtils.hasText(request.model()) ? request.model() : properties.chatModelOrDefault();
    }

    private String textOf(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
