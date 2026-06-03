package com.cx.ai_agent_backend.dto;

public class PinConversationRequest {

    private boolean pinned;

    public PinConversationRequest() {
    }

    public PinConversationRequest(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }
}
