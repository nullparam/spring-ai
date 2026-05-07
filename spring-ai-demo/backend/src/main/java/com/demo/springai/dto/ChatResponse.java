package com.demo.springai.dto;

public class ChatResponse {

    private String content;
    private String model;

    public ChatResponse() {}

    public ChatResponse(String content, String model) {
        this.content = content;
        this.model = model;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
