package com.cx.ai_agent_backend.controller;

import com.cx.ai_agent_backend.assistant.Assistant;
import com.cx.ai_agent_backend.dto.AiChatRequest;
import com.cx.ai_agent_backend.dto.AiChatResponse;
import com.cx.ai_agent_backend.dto.SimpleResponse;
import com.cx.ai_agent_backend.exception.AiServiceException;
import com.cx.ai_agent_backend.memory.ChatMemoryManager;
import com.cx.ai_agent_backend.service.AttachmentPromptService;
import com.cx.ai_agent_backend.service.ConversationStore;
import com.cx.ai_agent_backend.service.OpenAiVisionStreamService;
import com.cx.ai_agent_backend.service.WebSearchService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class AiChatController {

    private final Assistant assistant;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ChatMemoryManager chatMemoryManager;
    private final ConversationStore conversationStore;
    private final WebSearchService webSearchService;
    private final AttachmentPromptService attachmentPromptService;
    private final OpenAiVisionStreamService openAiVisionStreamService;

    public AiChatController(Assistant assistant,
                            StreamingChatLanguageModel streamingChatLanguageModel,
                            ChatMemoryManager chatMemoryManager,
                            ConversationStore conversationStore,
                            WebSearchService webSearchService,
                            AttachmentPromptService attachmentPromptService,
                            OpenAiVisionStreamService openAiVisionStreamService) {
        this.assistant = assistant;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.chatMemoryManager = chatMemoryManager;
        this.conversationStore = conversationStore;
        this.webSearchService = webSearchService;
        this.attachmentPromptService = attachmentPromptService;
        this.openAiVisionStreamService = openAiVisionStreamService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@RequestBody AiChatRequest aiChatRequest) {
        attachmentPromptService.validate(aiChatRequest);

        String conversationId = conversationStore.ensure(aiChatRequest.getConversationId()).getId();
        conversationStore.appendMessage(conversationId, "user", attachmentPromptService.displayMessage(aiChatRequest), System.currentTimeMillis());

        try {
            String prompt = buildPrompt(aiChatRequest);
            boolean hasImage = attachmentPromptService.hasImage(aiChatRequest);
            ChatMemory memory = chatMemoryManager.getMemory(conversationId);
            String rawAnswer = hasImage
                    ? openAiVisionStreamService.stream(aiChatRequest, prompt, memory.messages(), token -> {})
                    : assistant.chat(conversationId, prompt);
            if (hasImage) {
                memory.add(UserMessage.from(prompt));
                memory.add(AiMessage.from("我刚才根据用户上传的图片进行分析，结论如下：\n" + rawAnswer));
            }
            long timestamp = System.currentTimeMillis();
            conversationStore.appendMessage(conversationId, "assistant", rawAnswer, timestamp);
            return new AiChatResponse(rawAnswer, timestamp);
        } catch (RuntimeException e) {
            throw new AiServiceException("AI调用失败", e);
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamChat(@RequestBody AiChatRequest aiChatRequest) {
        attachmentPromptService.validate(aiChatRequest);

        SseEmitter emitter = new SseEmitter(120_000L);
        String conversationId = conversationStore.ensure(aiChatRequest.getConversationId()).getId();
        conversationStore.appendMessage(conversationId, "user", attachmentPromptService.displayMessage(aiChatRequest), System.currentTimeMillis());

        ChatMemory memory = chatMemoryManager.getMemory(conversationId);
        String prompt = buildPrompt(aiChatRequest);
        var historyMessages = memory.messages();
        memory.add(UserMessage.from(prompt));

        if (attachmentPromptService.hasImage(aiChatRequest)) {
            CompletableFuture.runAsync(() -> streamVision(aiChatRequest, emitter, conversationId, memory, prompt, historyMessages));
            return streamResponse(emitter);
        }

        StringBuilder answer = new StringBuilder();
        streamingChatLanguageModel.generate(memory.messages(), new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                answer.append(token);
                sendEvent(emitter, "token", token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                String finalAnswer = response.content() == null ? answer.toString() : response.content().text();
                memory.add(AiMessage.from(finalAnswer));
                conversationStore.appendMessage(conversationId, "assistant", finalAnswer, System.currentTimeMillis());
                sendEvent(emitter, "done", conversationId);
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                sendEvent(emitter, "error", "AI调用失败");
                emitter.completeWithError(new AiServiceException("AI调用失败", error));
            }
        });

        return streamResponse(emitter);
    }

    private void streamVision(AiChatRequest aiChatRequest,
                              SseEmitter emitter,
                              String conversationId,
                              ChatMemory memory,
                              String prompt,
                              java.util.List<dev.langchain4j.data.message.ChatMessage> historyMessages) {
        try {
            String finalAnswer = openAiVisionStreamService.stream(aiChatRequest, prompt, historyMessages, token -> sendEvent(emitter, "token", token));
            String memoryAnswer = finalAnswer;
            if (attachmentPromptService.hasImage(aiChatRequest)) {
                memoryAnswer = "我刚才根据用户上传的图片进行分析，结论如下：\n" + finalAnswer;
            }
            memory.add(AiMessage.from(memoryAnswer));
            conversationStore.appendMessage(conversationId, "assistant", finalAnswer, System.currentTimeMillis());
            sendEvent(emitter, "done", conversationId);
            emitter.complete();
        } catch (RuntimeException e) {
            sendEvent(emitter, "error", "当前模型或代理不支持图片识别，或图片识别调用失败");
            emitter.completeWithError(new AiServiceException("图片识别调用失败", e));
        }
    }

    private ResponseEntity<SseEmitter> streamResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @DeleteMapping("/chat/memory")
    public SimpleResponse clearMemory(@RequestBody(required = false) AiChatRequest aiChatRequest) {
        String conversationId = aiChatRequest == null ? null : aiChatRequest.getConversationId();
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new IllegalArgumentException("会话ID不能为空");
        }

        String memoryId = conversationId.trim();
        chatMemoryManager.clearMemory(memoryId);
        conversationStore.clearMessages(memoryId);
        return new SimpleResponse("记忆已清空");
    }

    private String buildPrompt(AiChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        boolean contextFollowUp = attachmentPromptService.isContextFollowUp(request);
        if (request.isDeepThinking()) {
            prompt.append("请使用更深入的分析方式回答。先输出一段标题为“思路摘要”的简短分析，说明你会如何拆解问题；再输出“正式回答”。不要暴露隐藏推理链，不要编造事实。\n\n");
        }
        if (contextFollowUp) {
            prompt.append("这是一个依赖上文的追问。请优先结合本会话前面用户上传的图片、页面内容和你已经给出的分析直接回答，不要把这句话拆成孤立问题，也不要转去解释词语本身。\n\n");
        }
        if (request.isWebSearch() && !contextFollowUp) {
            String searchContext = webSearchService.search(request.getMessage());
            prompt.append("以下是联网搜索得到的资料摘要，请优先基于这些资料回答，并说明信息可能存在时效性：\n")
                    .append(searchContext)
                    .append("\n\n");
        } else if (request.isWebSearch()) {
            prompt.append("本轮问题明显依赖上文，已跳过联网搜索，避免搜索结果覆盖会话上下文。\n\n");
        }
        prompt.append("用户问题：").append(request.getMessage() == null ? "" : request.getMessage().trim());
        return attachmentPromptService.appendAttachmentsToPrompt(prompt.toString(), request);
    }

    private void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
