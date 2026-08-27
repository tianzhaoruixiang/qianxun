package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.ChatSession;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.ChatMessageRepository;
import com.qianxun.repo.ChatSessionRepository;
import com.qianxun.service.stream.ActiveRunRegistry;
import com.qianxun.web.dto.ChatSessionListResponse;
import com.qianxun.web.dto.ChatSessionResponse;
import com.qianxun.web.dto.CreateSessionRequest;
import com.qianxun.web.dto.ListSessionsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QianXunServiceChatSessionTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private QianXunServiceActivityLog activityLogService;
    @Mock
    private AgentRegistryRepository agentRegistryRepository;
    @Mock
    private ActiveRunRegistry activeRunRegistry;

    @InjectMocks
    private QianXunServiceChatSession service;

    @BeforeEach
    void setUser() {
        UserContext.set("u1", "tester", "测试");
        lenient().when(sessionRepository.listAgentFacetsByUserId("u1")).thenReturn(List.of());
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void create_shouldPersistAgentFieldsAndChineseName() {
        when(agentRegistryRepository.findByCode("case-bot")).thenReturn(Optional.of(agent("case-bot", "案件研判", "case-bot")));

        ChatSessionResponse res = service.create(new CreateSessionRequest("新会话", "case-bot", "case-bot", ""));

        ArgumentCaptor<ChatSession> cap = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).insert(cap.capture());
        assertThat(cap.getValue().agentCode()).isEqualTo("case-bot");
        assertThat(cap.getValue().hermesProfile()).isEqualTo("case-bot");
        assertThat(cap.getValue().agentName()).isEqualTo("案件研判");
        assertThat(res.agentName()).isEqualTo("案件研判");
        assertThat(res.agentCode()).isEqualTo("case-bot");
    }

    @Test
    void create_withoutAgent_shouldLabelDigitalOfficer() {
        ChatSessionResponse res = service.create(new CreateSessionRequest("新会话", null, "default", null));
        ArgumentCaptor<ChatSession> cap = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).insert(cap.capture());
        assertThat(cap.getValue().agentName()).isEqualTo("数智干警");
        assertThat(res.agentName()).isEqualTo("数智干警");
    }

    @Test
    void list_legacyEmptyAgent_shouldBeDigitalOfficer() {
        Instant now = Instant.now();
        when(sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(21), eq(0), any())).thenReturn(List.of(
                stats("s1", now, 2, "hi", "", "", "", "")
        ));
        ChatSessionListResponse page = service.list(null);
        assertThat(page.items()).hasSize(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.limit()).isEqualTo(20);
        assertThat(page.offset()).isEqualTo(0);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.items().get(0).agentName()).isEqualTo("数智干警");
        assertThat(page.items().get(0).agentCode()).isNull();
        assertThat(page.items().get(0).messageCount()).isEqualTo(2);
    }

    @Test
    void list_shouldPreferLiveRegistryName() {
        Instant now = Instant.now();
        when(sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(21), eq(0), any())).thenReturn(List.of(
                stats("s2", now, 1, "x", "case-bot", "case-bot", "旧名", "")
        ));
        when(agentRegistryRepository.findByCode("case-bot")).thenReturn(Optional.of(agent("case-bot", "案件研判", "case-bot")));
        assertThat(service.list(null).items().get(0).agentName()).isEqualTo("案件研判");
    }

    @Test
    void list_pageTwo_shouldUseOffsetAndDetectHasMore() {
        Instant now = Instant.now();
        when(sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(3), eq(2), any())).thenReturn(List.of(
                stats("a", now, 1, "a", "", "", "", ""),
                stats("b", now, 1, "b", "", "", "", ""),
                stats("c", now, 1, "c", "", "", "", "")
        ));
        ChatSessionListResponse page = service.list(new ListSessionsRequest(2, 2, null));
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.offset()).isEqualTo(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.items()).extracting(ChatSessionResponse::id).containsExactly("a", "b");
    }

    @Test
    void list_defaultPageTwo_shouldOffsetByTwenty() {
        Instant now = Instant.now();
        when(sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(21), eq(20), any())).thenReturn(List.of(
                stats("p2", now, 1, "x", "", "", "", "")
        ));
        ChatSessionListResponse page = service.list(new ListSessionsRequest(2, null, null));
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.limit()).isEqualTo(20);
        assertThat(page.offset()).isEqualTo(20);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.items()).extracting(ChatSessionResponse::id).containsExactly("p2");
    }

    @Test
    void list_exactPageOfTwenty_shouldSetHasMoreFalse() {
        Instant now = Instant.now();
        when(sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(21), eq(0), any())).thenReturn(List.of(
                stats("only", now, 0, "", "", "", "", "")
        ));
        ChatSessionListResponse page = service.list(new ListSessionsRequest(1, 20, null));
        assertThat(page.limit()).isEqualTo(20);
        assertThat(page.offset()).isEqualTo(0);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void list_fullPagePlusOne_shouldHasMoreAndTrim() {
        Instant now = Instant.now();
        List<ChatSessionRepository.ChatSessionWithStats> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            rows.add(stats("s" + i, now, 0, "", "", "", "", ""));
        }
        when(sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(21), eq(0), any())).thenReturn(rows);
        ChatSessionListResponse page = service.list(new ListSessionsRequest(1, 20, null));
        assertThat(page.hasMore()).isTrue();
        assertThat(page.items()).hasSize(20);
        assertThat(page.items().get(19).id()).isEqualTo("s19");
    }

    @Test
    void list_offsetTakesPrecedenceOverPage() {
        Instant now = Instant.now();
        when(sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(11), eq(40), any())).thenReturn(List.of(
                stats("z", now, 0, "", "", "", "", "")
        ));
        ChatSessionListResponse page = service.list(new ListSessionsRequest(9, 10, 40));
        assertThat(page.offset()).isEqualTo(40);
        assertThat(page.page()).isEqualTo(5);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void list_cursor_shouldIgnoreOffsetAndPassFilter() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        when(sessionRepository.listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(21), eq(0), any())).thenReturn(List.of(
                stats("older", now, 0, "", "", "", "", "")
        ));
        ChatSessionListResponse page = service.list(new ListSessionsRequest(
                9, 20, 200, "案", "code:case-bot", "2026-08-27T01:00:00Z", "newer"
        ));
        assertThat(page.offset()).isEqualTo(0);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.items()).extracting(ChatSessionResponse::id).containsExactly("older");
        org.mockito.ArgumentCaptor<ChatSessionRepository.SessionListFilter> cap =
                org.mockito.ArgumentCaptor.forClass(ChatSessionRepository.SessionListFilter.class);
        verify(sessionRepository).listByUserIdWithStatsOrderByUpdatedDesc(eq("u1"), eq(21), eq(0), cap.capture());
        assertThat(cap.getValue().keyword()).isEqualTo("案");
        assertThat(cap.getValue().agentGroup()).isEqualTo("code:case-bot");
        assertThat(cap.getValue().cursorId()).isEqualTo("newer");
        assertThat(cap.getValue().cursorUpdatedAt()).isEqualTo(Instant.parse("2026-08-27T01:00:00Z"));
    }

    @Test
    void agentGroupKey_shouldMatchFrontendConvention() {
        assertThat(QianXunServiceChatSession.agentGroupKey("case-bot", "x", "案件")).isEqualTo("code:case-bot");
        assertThat(QianXunServiceChatSession.agentGroupKey("", "default", "")).isEqualTo("__digital_officer__");
        assertThat(QianXunServiceChatSession.agentGroupKey("", "worker", "未分类")).isEqualTo("uncat");
        assertThat(QianXunServiceChatSession.agentGroupKey("", "Worker", "自定义")).isEqualTo("profile:worker");
    }

    @Test
    void deleteByAgent_shouldCascadeMessagesAndSessions() {
        when(sessionRepository.listIdsByAgent("case-bot", "case-bot")).thenReturn(List.of("s1", "s2"));
        when(sessionRepository.deleteByIds(List.of("s1", "s2"))).thenReturn(2);
        when(activeRunRegistry.findRunning("s1")).thenReturn(Optional.empty());
        when(activeRunRegistry.findRunning("s2")).thenReturn(Optional.empty());

        int n = service.deleteByAgent("case-bot", "case-bot");

        assertThat(n).isEqualTo(2);
        verify(messageRepository).deleteBySessionId("s1");
        verify(messageRepository).deleteBySessionId("s2");
        verify(activityLogService).deleteBySession("s1");
        verify(activityLogService).deleteBySession("s2");
        verify(sessionRepository).deleteByIds(List.of("s1", "s2"));
    }

    @Test
    void deleteByAgent_shouldNoopWhenNoSessions() {
        when(sessionRepository.listIdsByAgent("gone", "")).thenReturn(List.of());
        assertThat(service.deleteByAgent("gone", "")).isEqualTo(0);
        verify(sessionRepository, org.mockito.Mockito.never()).deleteByIds(org.mockito.ArgumentMatchers.any());
    }

    private static ChatSessionRepository.ChatSessionWithStats stats(
            String id, Instant now, long count, String preview,
            String agentCode, String hermesProfile, String agentName, String sessionGoal
    ) {
        return new ChatSessionRepository.ChatSessionWithStats(
                id, "u1", "会话", now, now, count, preview, agentCode, hermesProfile, agentName, sessionGoal
        );
    }

    private static AgentRegistryItem agent(String code, String name, String profile) {
        Instant now = Instant.now();
        return new AgentRegistryItem(
                "id", code, name, "cat", "", "", "", "", "", "", "", "",
                "", "", "", profile, 0, true, now, now
        );
    }
}
