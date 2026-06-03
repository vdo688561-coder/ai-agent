package com.cx.ai_agent_backend.service;

import com.cx.ai_agent_backend.dto.AiAttachment;
import com.cx.ai_agent_backend.dto.AiChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentPromptServiceTests {

    private final AttachmentPromptService service = new AttachmentPromptService();

    @Test
    void acceptsAttachmentOnlyMessageAndBuildsDisplayText() {
        AiChatRequest request = new AiChatRequest("   ");
        request.setAttachments(List.of(new AiAttachment("截图.png", "image", "image/png", "data:image/png;base64,abc", 1200)));

        service.validate(request);

        assertThat(service.displayMessage(request)).isEqualTo("[附件：截图.png]");
    }

    @Test
    void appendsTextFileContentToPrompt() {
        AiChatRequest request = new AiChatRequest("帮我解释这个文件");
        request.setAttachments(List.of(new AiAttachment("Main.java", "text", "text/x-java", "class Main {}", 32)));

        String prompt = service.appendAttachmentsToPrompt("用户问题：帮我解释这个文件", request);

        assertThat(prompt)
                .contains("用户上传了以下文本文件")
                .contains("文件名：Main.java")
                .contains("class Main {}");
    }

    @Test
    void rejectsUnsupportedAttachmentType() {
        AiChatRequest request = new AiChatRequest("看看这个");
        request.setAttachments(List.of(new AiAttachment("app.exe", "binary", "application/octet-stream", "abc", 3)));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("暂不支持");
    }

    @Test
    void detectsShortContextFollowUp() {
        AiChatRequest request = new AiChatRequest("帮我判断一下");

        assertThat(service.isContextFollowUp(request)).isTrue();
    }
}
