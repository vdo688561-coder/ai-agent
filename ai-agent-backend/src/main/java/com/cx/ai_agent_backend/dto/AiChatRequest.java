package com.cx.ai_agent_backend.dto;

import java.util.ArrayList;
import java.util.List;

public class AiChatRequest {

    private String message;
    private String conversationId;
    private boolean deepThinking;
    private boolean webSearch;
    private List<AiAttachment> attachments = new ArrayList<>();

    public AiChatRequest() {
    }

    public AiChatRequest(String message) {
        this.message = message;
    }

    public AiChatRequest(String message, String conversationId) {
        this.message = message;
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public boolean isDeepThinking() {
        return deepThinking;
    }

    public void setDeepThinking(boolean deepThinking) {
        this.deepThinking = deepThinking;
    }

    public boolean isWebSearch() {
        return webSearch;
    }

    public void setWebSearch(boolean webSearch) {
        this.webSearch = webSearch;
    }

    public List<AiAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AiAttachment> attachments) {
        this.attachments = attachments == null ? new ArrayList<>() : attachments;
    }
}
