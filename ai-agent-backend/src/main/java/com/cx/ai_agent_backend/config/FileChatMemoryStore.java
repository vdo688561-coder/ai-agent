package com.cx.ai_agent_backend.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FileChatMemoryStore implements ChatMemoryStore {
    //记忆存储的文件路径
    private final Path baseDir = Paths.get("chat-hisories");
    public FileChatMemoryStore(){
        try{
            Files.createDirectories(baseDir);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    public List<ChatMessage> getMessages(Object memoryId){
        Path filePath = baseDir.resolve(memoryId.toString()+".json");
        if (!Files.exists(filePath)) {
            return List.of();
        }
        try {
            String json = Files.readString(filePath);
            return ChatMessageDeserializer.messagesFromJson(json);
        }catch (Exception e){
           return null;
        }
    }
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Path filePath = baseDir.resolve(memoryId.toString() + ".json");
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            Files.writeString(filePath, json);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try { Files.deleteIfExists(baseDir.resolve(memoryId.toString() + ".json")); } catch (IOException e) { e.printStackTrace(); }
    }

}
