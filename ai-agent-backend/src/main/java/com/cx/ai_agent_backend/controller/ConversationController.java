package com.cx.ai_agent_backend.controller;

import com.cx.ai_agent_backend.dto.ConversationDto;
import com.cx.ai_agent_backend.dto.PinConversationRequest;
import com.cx.ai_agent_backend.dto.RenameConversationRequest;
import com.cx.ai_agent_backend.service.ConversationStore;
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
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationStore conversationStore;

    public ConversationController(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
    }

    @GetMapping
    public List<ConversationDto> list() {
        return conversationStore.list();
    }

    @PostMapping
    public ConversationDto create() {
        return conversationStore.create();
    }

    @GetMapping("/{id}")
    public ConversationDto get(@PathVariable String id) {
        return conversationStore.get(id);
    }

    @PatchMapping("/{id}/title")
    public ConversationDto rename(@PathVariable String id,
                                  @RequestBody RenameConversationRequest request) {
        return conversationStore.rename(id, request.getTitle());
    }

    @PatchMapping("/{id}/pin")
    public ConversationDto pin(@PathVariable String id,
                               @RequestBody PinConversationRequest request) {
        return conversationStore.setPinned(id, request.isPinned());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        conversationStore.delete(id);
    }
}
