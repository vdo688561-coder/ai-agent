package com.cx.ai_agent_backend.exception;

import com.cx.ai_agent_backend.dto.AiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<AiErrorResponse> handleAiServiceException(AiServiceException e) {
        AiErrorResponse response = new AiErrorResponse("AI暂时不在家", 500);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AiErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        AiErrorResponse response = new AiErrorResponse(e.getMessage(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
