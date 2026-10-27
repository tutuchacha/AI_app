package com.tutuchacha.ai.config;

import com.tutuchacha.ai.gateway.AiProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 百炼（OpenAI 兼容模式）模型装配。
 * 只有配置了 api-key 才创建 ChatModel；没配时应用照常启动，
 * 调用聊天接口会返回 503 并提示配置密钥。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    @ConditionalOnExpression("'${app.ai.api-key:}'.trim().length() > 0")
    public ChatModel aiChatModel(AiProperties properties) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(properties.baseUrlOrDefault())
                .apiKey(properties.apiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.chatModelOrDefault())
                .temperature(properties.temperatureOrDefault())
                .maxTokens(properties.maxTokensOrDefault())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    /** 向量模型：与聊天共用 base-url/api-key，模型名默认 text-embedding-v3。 */
    @Bean
    @ConditionalOnExpression("'${app.ai.api-key:}'.trim().length() > 0")
    public EmbeddingModel aiEmbeddingModel(AiProperties properties) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(properties.baseUrlOrDefault())
                .apiKey(properties.apiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.embeddingModelOrDefault())
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, options);
    }
}
