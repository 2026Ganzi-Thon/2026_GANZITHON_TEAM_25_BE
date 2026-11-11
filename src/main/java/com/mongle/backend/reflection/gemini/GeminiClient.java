package com.mongle.backend.reflection.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gemini generateContent 얇은 래퍼. 공식 자바 SDK 대신 RestClient로 직접 친다 —
 * 모델명이 자주 바뀌는 해커톤 특성상 SDK 버전 호환성 문제를 피하려는 선택.
 */
@Slf4j
@Component
public class GeminiClient {

    /** 공감 응답 + Gemini 자체의 위험 신호 판정. 키워드 판정과 별개의 2차 의견이다. */
    public record ReflectionReply(String reply, boolean riskDetected) {}

    private static final GeminiRequest.GenerationConfig REFLECTION_CONFIG = new GeminiRequest.GenerationConfig(
            "application/json",
            new GeminiRequest.ResponseSchema(
                    "OBJECT",
                    Map.of(
                            "reply", new GeminiRequest.SchemaProperty("STRING"),
                            "riskDetected", new GeminiRequest.SchemaProperty("BOOLEAN")),
                    List.of("reply", "riskDetected")));

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(properties.getTimeoutMs()))
                .setResponseTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(properties.getTimeoutMs()))
                .build();
        var httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
    }

    /** 평문 응답 — 기억 요약처럼 구조화가 필요 없는 호출용. */
    public String generate(String systemInstruction, String userPrompt) {
        return call(systemInstruction, userPrompt, null);
    }

    /**
     * 공감 응답 + 위험 신호 판정을 한 번의 호출로 JSON으로 받는다(호출 수·지연을 늘리지 않으려는 선택).
     * JSON 파싱에 실패해도 GeminiCallException으로 묶어서 호출부가 키워드 폴백으로 떨어지게 한다.
     */
    public ReflectionReply generateReflection(String systemInstruction, String userPrompt) {
        return parseReflectionReply(call(systemInstruction, userPrompt, REFLECTION_CONFIG));
    }

    ReflectionReply parseReflectionReply(String json) {
        try {
            ReflectionReply parsed = objectMapper.readValue(json, ReflectionReply.class);
            if (parsed.reply() == null || parsed.reply().isBlank()) {
                throw new GeminiCallException("Gemini 구조화 응답에 reply가 비어 있습니다");
            }
            return parsed;
        } catch (JsonProcessingException e) {
            throw new GeminiCallException("Gemini 구조화 응답 파싱 실패", e);
        }
    }

    private String call(String systemInstruction, String userPrompt, GeminiRequest.GenerationConfig generationConfig) {
        if (!properties.isConfigured()) {
            throw new GeminiCallException("GEMINI_API_KEY가 설정되지 않았습니다");
        }
        try {
            GeminiRequest body = new GeminiRequest(
                    List.of(new GeminiRequest.Content("user", List.of(new GeminiRequest.Part(userPrompt)))),
                    new GeminiRequest.SystemInstruction(List.of(new GeminiRequest.Part(systemInstruction))),
                    generationConfig);

            GeminiResponse response = restClient.post()
                    .uri("/models/{model}:generateContent?key={key}", properties.getModel(), properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);

            return extractText(response)
                    .orElseThrow(() -> new GeminiCallException("Gemini 응답에서 텍스트를 찾을 수 없습니다"));
        } catch (RestClientException e) {
            log.warn("Gemini 호출 실패: {}", e.getMessage());
            throw new GeminiCallException("Gemini 호출 실패", e);
        }
    }

    private Optional<String> extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return Optional.empty();
        }
        var content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(content.parts().get(0).text());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GeminiRequest(
            List<Content> contents, SystemInstruction systemInstruction, GenerationConfig generationConfig) {
        private record Content(String role, List<Part> parts) {}
        private record SystemInstruction(List<Part> parts) {}
        private record Part(String text) {}
        private record GenerationConfig(String responseMimeType, ResponseSchema responseSchema) {}
        private record ResponseSchema(String type, Map<String, SchemaProperty> properties, List<String> required) {}
        private record SchemaProperty(String type) {}
    }

    private record GeminiResponse(List<Candidate> candidates) {
        private record Candidate(Content content) {}
        private record Content(List<Part> parts) {}
        private record Part(String text) {}
    }
}
