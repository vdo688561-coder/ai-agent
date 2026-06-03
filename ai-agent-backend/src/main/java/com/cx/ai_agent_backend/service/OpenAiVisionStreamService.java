package com.cx.ai_agent_backend.service;

import com.cx.ai_agent_backend.dto.AiAttachment;
import com.cx.ai_agent_backend.dto.AiChatRequest;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Service
public class OpenAiVisionStreamService {

    private final Gson gson = new Gson();
    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final AttachmentPromptService attachmentPromptService;

    public OpenAiVisionStreamService(@Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
                                     @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
                                     @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
                                     AttachmentPromptService attachmentPromptService) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.attachmentPromptService = attachmentPromptService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public String stream(AiChatRequest request, String prompt, List<ChatMessage> historyMessages, Consumer<String> tokenConsumer) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(request, prompt, historyMessages), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("图片识别调用失败：" + body);
            }

            return readStream(response, tokenConsumer);
        } catch (IOException e) {
            throw new IllegalStateException("图片识别网络调用失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片识别调用被中断", e);
        }
    }

    String buildBody(AiChatRequest request, String prompt, List<ChatMessage> historyMessages) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("stream", true);

        JsonArray messages = new JsonArray();
        for (ChatMessage historyMessage : historyMessages) {
            JsonObject message = toOpenAiMessage(historyMessage);
            if (message != null) {
                messages.add(message);
            }
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");

        JsonArray content = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", prompt);
        content.add(textPart);

        for (AiAttachment attachment : attachmentPromptService.imageAttachments(request)) {
            JsonObject imagePart = new JsonObject();
            imagePart.addProperty("type", "image_url");
            JsonObject imageUrl = new JsonObject();
            imageUrl.addProperty("url", normalizeImageDataUrl(attachment));
            imagePart.add("image_url", imageUrl);
            content.add(imagePart);
        }

        userMessage.add("content", content);
        messages.add(userMessage);
        body.add("messages", messages);
        return gson.toJson(body);
    }

    private JsonObject toOpenAiMessage(ChatMessage historyMessage) {
        if (historyMessage == null || historyMessage.text() == null || historyMessage.text().isBlank()) {
            return null;
        }
        JsonObject message = new JsonObject();
        switch (historyMessage.type()) {
            case USER -> message.addProperty("role", "user");
            case AI -> message.addProperty("role", "assistant");
            case SYSTEM -> message.addProperty("role", "system");
            default -> {
                return null;
            }
        }
        message.addProperty("content", historyMessage.text());
        return message;
    }

    private String readStream(HttpResponse<java.io.InputStream> response, Consumer<String> tokenConsumer) throws IOException {
        StringBuilder answer = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                String token = extractToken(data);
                if (!token.isEmpty()) {
                    answer.append(token);
                    tokenConsumer.accept(token);
                }
            }
        }
        return answer.toString();
    }

    private String extractToken(String data) {
        JsonObject json = gson.fromJson(data, JsonObject.class);
        JsonArray choices = json.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject delta = choice.getAsJsonObject("delta");
        if (delta != null && delta.has("content") && !delta.get("content").isJsonNull()) {
            return delta.get("content").getAsString();
        }
        if (choice.has("message")) {
            JsonObject message = choice.getAsJsonObject("message");
            if (message.has("content") && !message.get("content").isJsonNull()) {
                return message.get("content").getAsString();
            }
        }
        return "";
    }

    private String normalizeImageDataUrl(AiAttachment attachment) {
        String data = attachment.getData().trim();
        if (data.startsWith("data:")) {
            return data;
        }
        return "data:" + attachment.getMimeType() + ";base64," + data;
    }

    private String normalizeBaseUrl(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
