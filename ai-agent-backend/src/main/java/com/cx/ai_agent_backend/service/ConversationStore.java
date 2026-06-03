package com.cx.ai_agent_backend.service;

import com.cx.ai_agent_backend.dto.ChatMessageDto;
import com.cx.ai_agent_backend.dto.ConversationDto;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationStore {

    private static final String DEFAULT_TITLE = "新的对话";
    private static final Type CONVERSATION_LIST_TYPE = new TypeToken<List<ConversationDto>>() {
    }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path storageFile;
    private final Map<String, ConversationDto> conversations = new LinkedHashMap<>();

    public ConversationStore() {
        this(Paths.get("chat-history", "conversations.json"));
    }

    ConversationStore(Path storageFile) {
        this.storageFile = storageFile;
        load();
    }

    public synchronized List<ConversationDto> list() {
        return conversations.values()
                .stream()
                .sorted(Comparator
                        .comparing(ConversationDto::isPinned).reversed()
                        .thenComparing(ConversationDto::getUpdatedAt, Comparator.reverseOrder()))
                .map(this::copy)
                .toList();
    }

    public synchronized ConversationDto get(String id) {
        ConversationDto conversation = conversations.get(id);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        return copy(conversation);
    }

    public synchronized ConversationDto create() {
        long now = System.currentTimeMillis();
        ConversationDto conversation = new ConversationDto(
                UUID.randomUUID().toString(),
                DEFAULT_TITLE,
                false,
                now,
                now,
                new ArrayList<>()
        );
        conversations.put(conversation.getId(), conversation);
        save();
        return copy(conversation);
    }

    public synchronized ConversationDto ensure(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return create();
        }
        ConversationDto conversation = conversations.get(conversationId.trim());
        if (conversation != null) {
            return copy(conversation);
        }

        long now = System.currentTimeMillis();
        ConversationDto created = new ConversationDto(
                conversationId.trim(),
                DEFAULT_TITLE,
                false,
                now,
                now,
                new ArrayList<>()
        );
        conversations.put(created.getId(), created);
        save();
        return copy(created);
    }

    public synchronized ConversationDto appendMessage(String conversationId, String role, String content, long timestamp) {
        ConversationDto conversation = conversations.get(conversationId);
        if (conversation == null) {
            conversation = ensure(conversationId);
            conversation = conversations.get(conversation.getId());
        }

        conversation.getMessages().add(new ChatMessageDto(
                UUID.randomUUID().toString(),
                role,
                content,
                timestamp
        ));
        conversation.setUpdatedAt(timestamp);
        if (DEFAULT_TITLE.equals(conversation.getTitle()) && "user".equals(role)) {
            conversation.setTitle(buildTitle(content));
        }
        save();
        return copy(conversation);
    }

    public synchronized ConversationDto rename(String id, String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        ConversationDto conversation = conversations.get(id);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        conversation.setTitle(value.length() > 40 ? value.substring(0, 40) : value);
        conversation.setUpdatedAt(System.currentTimeMillis());
        save();
        return copy(conversation);
    }

    public synchronized ConversationDto setPinned(String id, boolean pinned) {
        ConversationDto conversation = conversations.get(id);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        conversation.setPinned(pinned);
        conversation.setUpdatedAt(System.currentTimeMillis());
        save();
        return copy(conversation);
    }

    public synchronized void delete(String id) {
        conversations.remove(id);
        save();
    }

    public synchronized ConversationDto clearMessages(String id) {
        ConversationDto conversation = conversations.get(id);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        conversation.getMessages().clear();
        conversation.setTitle(DEFAULT_TITLE);
        conversation.setUpdatedAt(System.currentTimeMillis());
        save();
        return copy(conversation);
    }

    private void load() {
        try {
            Files.createDirectories(storageFile.getParent());
            if (!Files.exists(storageFile)) {
                return;
            }
            String json = Files.readString(storageFile, StandardCharsets.UTF_8);
            if (json.trim().isEmpty()) {
                return;
            }
            List<ConversationDto> loaded = gson.fromJson(json, CONVERSATION_LIST_TYPE);
            if (loaded == null) {
                return;
            }
            for (ConversationDto conversation : loaded) {
                if (conversation.getId() != null && !conversation.getId().isBlank()) {
                    conversation.setMessages(conversation.getMessages());
                    conversations.put(conversation.getId(), conversation);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取会话历史失败", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(storageFile.getParent());
            Files.writeString(storageFile, gson.toJson(new ArrayList<>(conversations.values())), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("保存会话历史失败", e);
        }
    }

    private ConversationDto copy(ConversationDto source) {
        List<ChatMessageDto> messages = source.getMessages()
                .stream()
                .map(message -> new ChatMessageDto(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getTimestamp()
                ))
                .toList();
        return new ConversationDto(
                source.getId(),
                source.getTitle(),
                source.isPinned(),
                source.getCreatedAt(),
                source.getUpdatedAt(),
                messages
        );
    }

    private String buildTitle(String content) {
        String compact = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        if (compact.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return compact.length() > 18 ? compact.substring(0, 18) + "..." : compact;
    }
}
