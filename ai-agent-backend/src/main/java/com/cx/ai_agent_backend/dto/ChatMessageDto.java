package com.cx.ai_agent_backend.dto;

public class ChatMessageDto {

    private String id;
    private String role;
    private String content;
    private long timestamp;

    public ChatMessageDto() {
    }

    public ChatMessageDto(String id, String role, String content, long timestamp) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
