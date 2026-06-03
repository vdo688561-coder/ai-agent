package com.cx.ai_agent_backend.service;

import com.cx.ai_agent_backend.dto.ConversationDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationStoreTests {

    @TempDir
    Path tempDir;

    @Test
    void reloadsConversationsFromDisk() {
        Path storageFile = tempDir.resolve("chat-history").resolve("conversations.json");
        ConversationStore firstStore = new ConversationStore(storageFile);

        ConversationDto conversation = firstStore.create();
        firstStore.appendMessage(conversation.getId(), "user", "我叫小明", 1000L);
        firstStore.appendMessage(conversation.getId(), "assistant", "你好，小明", 1001L);

        ConversationStore secondStore = new ConversationStore(storageFile);
        List<ConversationDto> conversations = secondStore.list();

        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).getMessages())
                .extracting("content")
                .containsExactly("我叫小明", "你好，小明");
    }

    @Test
    void clearMessagesPersistsAfterReload() {
        Path storageFile = tempDir.resolve("chat-history").resolve("conversations.json");
        ConversationStore firstStore = new ConversationStore(storageFile);

        ConversationDto conversation = firstStore.create();
        firstStore.appendMessage(conversation.getId(), "user", "第一句话", 1000L);
        firstStore.clearMessages(conversation.getId());

        ConversationStore secondStore = new ConversationStore(storageFile);

        assertThat(secondStore.get(conversation.getId()).getMessages()).isEmpty();
    }
}
