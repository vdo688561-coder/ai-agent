package com.cx.ai_agent_backend.dto;

public class AiChatResponse {

    private String answer;
    private long timestamp;

    public AiChatResponse() {
    }

    public AiChatResponse(String answer) {
        this.answer = answer;
        this.timestamp = System.currentTimeMillis();
    }

    public AiChatResponse(String answer, long timestamp) {
        this.answer = answer;
        this.timestamp = timestamp;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
