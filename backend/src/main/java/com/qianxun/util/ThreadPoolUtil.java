package com.qianxun.util;

import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

@Component
public class ThreadPoolUtil {
    private static final int QUEUE_CAPACITY = 5000;


    @Bean
    public ThreadPoolTaskExecutor briefingThreadPool(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(20);
        executor.setThreadNamePrefix("briefing-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor realTimeKeywordThreadPool(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(1000); // 设置队列容量
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("realTimeKeyword-");
        executor.initialize();
        return executor;
    }
}
