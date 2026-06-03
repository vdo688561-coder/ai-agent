package com.cx.ai_agent_backend.config;

import com.cx.ai_agent_backend.assistant.Assistant;
import com.cx.ai_agent_backend.memory.ChatMemoryManager;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {


    @Bean
    public Assistant assistant(ChatLanguageModel model, ChatMemoryManager chatMemoryManager) {
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(chatMemoryManager::getMemory)
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl
    ) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

}
