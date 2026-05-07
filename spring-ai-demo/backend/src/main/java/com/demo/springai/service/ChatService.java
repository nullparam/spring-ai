package com.demo.springai.service;

import com.demo.springai.dto.ChatRequest;
import com.demo.springai.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ChatService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public Mono<ChatResponse> chat(ChatRequest request) {
        ObjectNode body = buildRequestBody(request.getMessage(), false);

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + request.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String content = json.path("choices").path(0).path("message").path("content").asText("");
                    return new ChatResponse(content, "mimo-v2.5-pro");
                });
    }

    public Flux<String> chatStream(ChatRequest request) {
        ObjectNode body = buildRequestBody(request.getMessage(), true);

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + request.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && !line.equals("[DONE]"))
                .map(line -> {
                    if (line.startsWith("data: ")) {
                        return line.substring(6);
                    }
                    return line;
                })
                .filter(line -> !line.isBlank() && !line.equals("[DONE]"))
                .mapNotNull(line -> {
                    try {
                        JsonNode json = objectMapper.readTree(line);
                        String delta = json.path("choices").path(0).path("delta").path("content").asText(null);
                        if (delta != null && !delta.isEmpty()) {
                            return "data: " + delta + "\n\n";
                        }
                    } catch (Exception ignored) {
                    }
                    return null;
                })
                .concatWithValues("data: [DONE]\n\n");
    }

    private ObjectNode buildRequestBody(String message, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "mimo-v2.5-pro");
        body.put("stream", stream);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2048);

        ArrayNode messages = body.putArray("messages");

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a helpful AI assistant powered by mimo-v2.5-pro.");

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", message);

        return body;
    }
}
