package com.cx.ai_agent_backend.dto;

import java.util.ArrayList;
import java.util.List;

public class ConversationDto {

    private String id;
    private String title;
    private boolean pinned;
    private long createdAt;
    private long updatedAt;
    private List<ChatMessageDto> messages = new ArrayList<>();

    public ConversationDto() {
    }

    public ConversationDto(String id, String title, boolean pinned, long createdAt, long updatedAt, List<ChatMessageDto> messages) {
        this.id = id;
        this.title = title;
        this.pinned = pinned;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messages = messages == null ? new ArrayList<>() : messages;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessageDto> messages) {
        this.messages = messages == null ? new ArrayList<>() : messages;
    }
}
