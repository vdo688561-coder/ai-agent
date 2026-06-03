package com.cx.ai_agent_backend.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMemoryManager {

    private static final int MAX_MESSAGES = 80;

    private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

    public ChatMemory getMemory(Object memoryId) {
        return memories.computeIfAbsent(memoryId, id ->
                MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(MAX_MESSAGES)
                        .build()
        );
    }

    public void clearMemory(Object memoryId) {
        ChatMemory memory = memories.remove(memoryId);
        if (memory != null) {
            memory.clear();
        }
    }
}
