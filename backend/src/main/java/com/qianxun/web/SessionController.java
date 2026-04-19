package com.qianxun.web;

import com.qianxun.service.QianXunChatSessionService;
import com.qianxun.web.dto.ChatMessageResponse;
import com.qianxun.web.dto.ChatSessionResponse;
import com.qianxun.web.dto.CreateSessionRequest;
import com.qianxun.web.dto.UpdateSessionRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final QianXunChatSessionService chatSessionService;

    public SessionController(QianXunChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public ChatSessionResponse create(@RequestBody(required = false) CreateSessionRequest request) {
        return chatSessionService.create(request);
    }

    @GetMapping
    public List<ChatSessionResponse> list() {
        return chatSessionService.list();
    }

    @GetMapping("/{id}")
    public ChatSessionResponse get(@PathVariable("id") String id) {
        return chatSessionService.get(id);
    }

    @PatchMapping("/{id}")
    public ChatSessionResponse update(@PathVariable("id") String id, @RequestBody UpdateSessionRequest request) {
        return chatSessionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) {
        chatSessionService.delete(id);
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageResponse> messages(@PathVariable("id") String id) {
        return chatSessionService.listMessages(id);
    }
}
