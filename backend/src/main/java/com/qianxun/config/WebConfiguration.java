package com.qianxun.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.qianxun.security.JwtAuthenticationFilter;
import com.qianxun.security.JwtService;
import com.qianxun.web.interceptor.UserContextInterceptor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(QianxunProperties.class)
public class WebConfiguration implements WebMvcConfigurer {

    private final QianxunProperties properties;

    public WebConfiguration(QianxunProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor()).addPathPatterns("/QianXunService/**");
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        // 经前端 nginx 反代时，浏览器 Origin 是「访问用的 IP:80」，后端实际端口是 8080，
        // Spring 会判为跨域。仅允许 localhost 时，内网用 IP/主机名打开页面 → 登录 OPTIONS/POST 直接 403。
        String[] patterns = properties.getCors().resolvedOriginPatterns();
        registry.addMapping("/QianXunService/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Cache-Control", "Content-Type", "Content-Disposition", "X-Trace-Id");
    }

    @Bean
    @Qualifier("sseExecutor")
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("qianxun-sse-");
        ex.setDaemon(true);
        ex.initialize();
        return ex;
    }

    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        // 0 = 不限制。智能体一轮可能远超 10 分钟（工具/思考）；具体保活由 SSE comment 心跳承担
        configurer.setDefaultTimeout(0);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(QianxunProperties properties, JwtService jwtService) {
        return new JwtAuthenticationFilter(properties, jwtService);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/QianXunService/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return reg;
    }
}
