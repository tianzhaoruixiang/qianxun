package com.qianxun.web.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.context.UserContext;
import com.qianxun.security.JwtService;
import com.qianxun.service.HermesLiveTranscriptService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 委派 live transcript 实时推送：客户端 subscribe 后按 intervalMs 轮询并推送 JSON 事件。
 */
public class DelegationTranscriptWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DelegationTranscriptWebSocketHandler.class);

    private final JwtService jwtService;
    private final HermesLiveTranscriptService transcriptService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "delegation-ws");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public DelegationTranscriptWebSocketHandler(JwtService jwtService, HermesLiveTranscriptService transcriptService) {
        this.jwtService = jwtService;
        this.transcriptService = transcriptService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = authenticate(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未授权"));
            return;
        }
        UserContext.set(userId, userId, userId);
        sessions.put(session.getId(), new SessionState(session, userId));
        sendJson(session, Map.of("type", "connected", "userId", userId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            return;
        }
        UserContext.set(state.userId, state.userId, state.userId);
        Map<String, Object> cmd = objectMapper.readValue(message.getPayload(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        String action = str(cmd.get("action"));
        if ("subscribe".equalsIgnoreCase(action)) {
            state.profile = str(cmd.get("profile"));
            state.delegationId = str(cmd.get("delegationId"));
            state.taskIndex = parseInt(cmd.get("taskIndex"));
            state.intervalMs = Math.min(Math.max(parseInt(cmd.get("intervalMs")) == null ? 2000 : parseInt(cmd.get("intervalMs")), 500), 15000);
            restartPoll(state);
            sendJson(session, Map.of("type", "subscribed", "profile", state.profile, "delegationId", state.delegationId == null ? "" : state.delegationId));
            return;
        }
        if ("list".equalsIgnoreCase(action)) {
            state.profile = str(cmd.get("profile"));
            int limit = parseInt(cmd.get("limit")) == null ? 8 : parseInt(cmd.get("limit"));
            List<HermesLiveTranscriptService.DelegationInfo> list = transcriptService.listRecent(state.profile, limit);
            sendJson(session, Map.of("type", "delegation_list", "items", list));
            return;
        }
        if ("ping".equalsIgnoreCase(action)) {
            sendJson(session, Map.of("type", "pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = sessions.remove(session.getId());
        if (state != null && state.pollTask != null) {
            state.pollTask.cancel(true);
        }
        UserContext.clear();
    }

    private void restartPoll(SessionState state) {
        if (state.pollTask != null) {
            state.pollTask.cancel(true);
        }
        if (state.delegationId == null || state.delegationId.isBlank()) {
            state.pollTask = scheduler.scheduleAtFixedRate(() -> pollList(state), 0, state.intervalMs, TimeUnit.MILLISECONDS);
        } else {
            state.pollTask = scheduler.scheduleAtFixedRate(() -> pollOne(state), 0, state.intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    private void pollList(SessionState state) {
        try {
            UserContext.set(state.userId, state.userId, state.userId);
            List<HermesLiveTranscriptService.DelegationInfo> list = transcriptService.listRecent(state.profile, 12);
            sendJson(state.session, Map.of("type", "delegation_list", "items", list, "ts", System.currentTimeMillis()));
        } catch (Exception ex) {
            log.debug("WS list poll failed: {}", ex.toString());
        }
    }

    private void pollOne(SessionState state) {
        try {
            UserContext.set(state.userId, state.userId, state.userId);
            HermesLiveTranscriptService.DelegationInfo info = transcriptService.loadDelegation(state.profile, state.delegationId);
            HermesLiveTranscriptService.LogContent log = transcriptService.readTaskLog(
                    state.profile, state.delegationId, state.taskIndex, 24000);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "delegation_update");
            payload.put("ts", System.currentTimeMillis());
            payload.put("delegation", info);
            payload.put("content", log.ok() ? log.content() : "");
            payload.put("ok", log.ok());
            if (!log.ok()) {
                payload.put("message", log.message());
            }
            sendJson(state.session, payload);
        } catch (Exception ex) {
            log.debug("WS transcript poll failed: {}", ex.toString());
        }
    }

    private void sendJson(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception ex) {
            log.debug("WS send failed: {}", ex.toString());
        }
    }

    private String authenticate(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String token = null;
        for (String part : uri.getQuery().split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if ("token".equals(part.substring(0, eq))) {
                token = part.substring(eq + 1);
                break;
            }
        }
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = jwtService.parseAndValidate(token);
            Object uid = claims.get(JwtService.CLAIM_USER_ID);
            return uid == null ? claims.getSubject() : String.valueOf(uid);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Integer parseInt(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class SessionState {
        private final WebSocketSession session;
        private final String userId;
        private String profile = "";
        private String delegationId = "";
        private Integer taskIndex;
        private int intervalMs = 2000;
        private volatile ScheduledFuture<?> pollTask;

        private SessionState(WebSocketSession session, String userId) {
            this.session = session;
            this.userId = userId;
        }
    }
}
