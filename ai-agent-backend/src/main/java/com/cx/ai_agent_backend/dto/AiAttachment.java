package com.cx.ai_agent_backend.dto;

public class AiAttachment {

    private String name;
    private String type;
    private String mimeType;
    private String data;
    private long size;

    public AiAttachment() {
    }

    public AiAttachment(String name, String type, String mimeType, String data, long size) {
        this.name = name;
        this.type = type;
        this.mimeType = mimeType;
        this.data = data;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
