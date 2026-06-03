package com.cx.ai_agent_backend.service;

import com.cx.ai_agent_backend.dto.AiAttachment;
import com.cx.ai_agent_backend.dto.AiChatRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class AttachmentPromptService {

    private static final int MAX_ATTACHMENTS = 8;
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_TEXT_SIZE = 200L * 1024;
    private static final int MAX_TEXT_CHARS = 60_000;

    public void validate(AiChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        List<AiAttachment> attachments = request.getAttachments();
        if (message.isEmpty() && attachments.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (attachments.size() > MAX_ATTACHMENTS) {
            throw new IllegalArgumentException("一次最多上传 8 个附件");
        }

        for (AiAttachment attachment : attachments) {
            String type = normalize(attachment.getType());
            String mimeType = normalize(attachment.getMimeType());
            if (!"image".equals(type) && !"text".equals(type)) {
                throw new IllegalArgumentException("暂不支持该附件类型");
            }
            if ("image".equals(type)) {
                if (!mimeType.startsWith("image/")) {
                    throw new IllegalArgumentException("图片附件格式不正确");
                }
                if (attachment.getSize() > MAX_IMAGE_SIZE) {
                    throw new IllegalArgumentException("单张图片不能超过 5MB");
                }
            }
            if ("text".equals(type) && attachment.getSize() > MAX_TEXT_SIZE) {
                throw new IllegalArgumentException("单个文本文件不能超过 200KB");
            }
            if (attachment.getData() == null || attachment.getData().isBlank()) {
                throw new IllegalArgumentException("附件内容不能为空");
            }
        }
    }

    public boolean hasImage(AiChatRequest request) {
        return request.getAttachments().stream()
                .anyMatch(attachment -> "image".equals(normalize(attachment.getType())));
    }

    public String displayMessage(AiChatRequest request) {
        StringBuilder display = new StringBuilder();
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (!message.isEmpty()) {
            display.append(message);
        }
        for (AiAttachment attachment : request.getAttachments()) {
            if (!display.isEmpty()) {
                display.append("\n");
            }
            display.append("[附件：").append(safeName(attachment)).append("]");
        }
        return display.toString();
    }

    public String appendAttachmentsToPrompt(String basePrompt, AiChatRequest request) {
        StringBuilder prompt = new StringBuilder(basePrompt);
        List<AiAttachment> textAttachments = request.getAttachments().stream()
                .filter(attachment -> "text".equals(normalize(attachment.getType())))
                .toList();
        if (textAttachments.isEmpty()) {
            return prompt.toString();
        }

        prompt.append("\n\n用户上传了以下文本文件，请结合文件内容回答：");
        for (AiAttachment attachment : textAttachments) {
            prompt.append("\n\n文件名：").append(safeName(attachment))
                    .append("\n内容：\n")
                    .append(limitText(attachment.getData()));
        }
        return prompt.toString();
    }

    public List<AiAttachment> imageAttachments(AiChatRequest request) {
        return request.getAttachments().stream()
                .filter(attachment -> "image".equals(normalize(attachment.getType())))
                .toList();
    }

    public boolean isContextFollowUp(AiChatRequest request) {
        if (!request.getAttachments().isEmpty()) {
            return false;
        }
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        String compact = message.replaceAll("\\s+", "");
        if (compact.isEmpty() || compact.length() > 24) {
            return false;
        }
        return compact.matches(".*(继续|接着|上面|刚才|这个|这张图|它|帮我判断|判断一下|分析一下|详细说说|是什么意思|是什么|怎么做).*");
    }

    private String safeName(AiAttachment attachment) {
        String name = attachment.getName() == null ? "未命名附件" : attachment.getName().trim();
        if (name.isEmpty()) {
            return "未命名附件";
        }
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    private String limitText(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_TEXT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TEXT_CHARS) + "\n\n[文件内容过长，已截断]";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
