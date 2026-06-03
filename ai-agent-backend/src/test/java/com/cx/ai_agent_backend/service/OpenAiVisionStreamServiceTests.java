package com.cx.ai_agent_backend.service;

import com.cx.ai_agent_backend.dto.AiAttachment;
import com.cx.ai_agent_backend.dto.AiChatRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiVisionStreamServiceTests {

    @Test
    void buildsVisionRequestWithConversationHistory() {
        AttachmentPromptService attachmentPromptService = new AttachmentPromptService();
        OpenAiVisionStreamService service = new OpenAiVisionStreamService(
                "test-key",
                "test-model",
                "https://example.com/v1",
                attachmentPromptService
        );
        AiChatRequest request = new AiChatRequest("帮我判断一下");
        request.setAttachments(List.of(new AiAttachment("screenshot.png", "image", "image/png", "data:image/png;base64,abc", 128)));

        String body = service.buildBody(
                request,
                "用户问题：帮我判断一下",
                List.of(
                        UserMessage.from("用户问题：这张图是什么内容？"),
                        AiMessage.from("这张图展示了 DeepSeek 风格的聊天页面，可以继续判断页面用途。")
                )
        );

        assertThat(body)
                .contains("\"role\":\"user\"")
                .contains("\"role\":\"assistant\"")
                .contains("这张图展示了 DeepSeek 风格的聊天页面")
                .contains("\"image_url\"");
    }
}
