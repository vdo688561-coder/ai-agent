package com.cx.ai_agent_backend.exception;

public class AiServiceException extends RuntimeException{
    public AiServiceException(String message,Throwable cause){
        super(message,cause);
    }
}
