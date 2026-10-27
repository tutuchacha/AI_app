package com.tutuchacha.ai.knowledge;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 解析线程池：上传接口立即返回 PENDING，解析在后台跑，避免 HTTP 等 Tika。 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean(name = "ragExecutor")
    public Executor ragExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("rag-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
