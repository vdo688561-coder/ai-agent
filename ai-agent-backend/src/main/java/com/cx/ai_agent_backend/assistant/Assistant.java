package com.cx.ai_agent_backend.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

public interface Assistant {
    String chat(@MemoryId String memoryId,@UserMessage String message);
}
