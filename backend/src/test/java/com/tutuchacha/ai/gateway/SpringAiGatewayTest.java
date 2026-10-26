package com.tutuchacha.ai.gateway;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiGatewayTest {

    private final ChatModel chatModel = mock(ChatModel.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);

    private final AiProperties properties = new AiProperties(
            "https://dashscope.aliyuncs.com/compatible-mode/v1", "test-key", "qwen-plus", 0.7, 1024, null);

    @Test
    void mapsRequestToSpringAiAndBack() {
        when(provider.getIfAvailable()).thenReturn(chatModel);
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage("你好，我是星屿"))),
                ChatResponseMetadata.builder()
                        .model("qwen-plus")
                        .usage(new DefaultUsage(12, 7))
                        .build());
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        AiChatResponse result = new SpringAiGateway(provider, properties)
                .complete(AiChatRequest.of("你是助手", "你好"));

        assertThat(result.content()).isEqualTo("你好，我是星屿");
        assertThat(result.model()).isEqualTo("qwen-plus");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.completionTokens()).isEqualTo(7);
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(captor.capture());
        List<Message> messages = captor.getValue().getInstructions();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getText()).isEqualTo("你是助手");
        assertThat(messages.get(1).getText()).isEqualTo("你好");
    }

    @Test
    void returns503WhenApiKeyMissing() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> new SpringAiGateway(provider, properties)
                .complete(AiChatRequest.of(null, "你好")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERVICE_UNAVAILABLE)
                .hasMessageContaining("DASHSCOPE_API_KEY");
    }

    @Test
    void wrapsUpstreamFailureAs502() {
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> new SpringAiGateway(provider, properties)
                .complete(AiChatRequest.of(null, "你好")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_GATEWAY);
    }

    @Test
    void mapsUpstream429ToTooManyRequests() {
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("429 rate limit"));

        assertThatThrownBy(() -> new SpringAiGateway(provider, properties)
                .complete(AiChatRequest.of(null, "你好")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void streamsDeltasAndFinalUsage() {
        when(provider.getIfAvailable()).thenReturn(chatModel);
        ChatResponse first = new ChatResponse(
                List.of(new Generation(new AssistantMessage("你"))),
                ChatResponseMetadata.builder().model("qwen-plus").build());
        ChatResponse last = new ChatResponse(
                List.of(new Generation(new AssistantMessage(""))),
                ChatResponseMetadata.builder().model("qwen-plus").usage(new DefaultUsage(5, 1)).build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(first, last));

        List<AiStreamChunk> chunks = new SpringAiGateway(provider, properties)
                .stream(AiChatRequest.of(null, "你好"))
                .collectList()
                .block();

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("你");
        assertThat(chunks.get(1).hasUsage()).isTrue();
        assertThat(chunks.get(1).promptTokens()).isEqualTo(5);
        assertThat(chunks.get(1).completionTokens()).isEqualTo(1);
    }
}
