package com.cx.ai_agent_backend.dto;

public class RenameConversationRequest {

    private String title;

    public RenameConversationRequest() {
    }

    public RenameConversationRequest(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
