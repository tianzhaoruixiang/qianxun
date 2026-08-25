package com.qianxun.config;

import com.qianxun.security.JwtService;
import com.qianxun.service.HermesLiveTranscriptService;
import com.qianxun.web.ws.DelegationTranscriptWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private final JwtService jwtService;
    private final HermesLiveTranscriptService transcriptService;

    public WebSocketConfiguration(JwtService jwtService, HermesLiveTranscriptService transcriptService) {
        this.jwtService = jwtService;
        this.transcriptService = transcriptService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                new DelegationTranscriptWebSocketHandler(jwtService, transcriptService),
                "/QianXunService/ws/delegation"
        ).setAllowedOriginPatterns("*");
    }
}
