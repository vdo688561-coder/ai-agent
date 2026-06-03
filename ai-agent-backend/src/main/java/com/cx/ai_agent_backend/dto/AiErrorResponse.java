package com.cx.ai_agent_backend.dto;

public record AiErrorResponse(
        String error,
        int code
) {
}
