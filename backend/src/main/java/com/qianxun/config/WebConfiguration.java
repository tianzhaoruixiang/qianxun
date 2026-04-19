package com.qianxun.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.qianxun.web.interceptor.UserContextInterceptor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(QianxunProperties.class)
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor()).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:3000",
                        "http://127.0.0.1:3000"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Cache-Control", "Content-Type");
    }

    @Bean
    @Qualifier("sseExecutor")
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("qianxun-sse-");
        ex.initialize();
        return ex;
    }

    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(600_000);
    }
}
