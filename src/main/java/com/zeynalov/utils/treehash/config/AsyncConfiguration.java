package com.zeynalov.utils.treehash.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

    private static final String EXECUTOR_NAME = "ASYNC-";

    @Value("${async.threads:#{2}}")
    private int maxThreads = 2;

    @Bean
    public Executor asyncExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        if (maxThreads == 0) {
            executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        } else {
            executor.setCorePoolSize(maxThreads);
        }
        executor.setThreadNamePrefix(EXECUTOR_NAME);
        executor.initialize();
        return executor;
    }

}
