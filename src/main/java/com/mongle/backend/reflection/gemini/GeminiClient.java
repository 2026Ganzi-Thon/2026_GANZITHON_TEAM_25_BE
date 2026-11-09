package com.mongle.backend.reflection.gemini;

import java.time.Duration;
import java.util.List;
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

    private final GeminiProperties properties;
    private final RestClient restClient;

    public GeminiClient(GeminiProperties properties) {
        this.properties = properties;
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(properties.getTimeoutMs()))
                .setResponseTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(properties.getTimeoutMs()))
                .build();
        var httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
    }

    /** systemInstruction은 톤·안전 규칙, userPrompt는 실제 사용자 발화 + 선택된 기억 요약을 합친 것. */
    public String generate(String systemInstruction, String userPrompt) {
        if (!properties.isConfigured()) {
            throw new GeminiCallException("GEMINI_API_KEY가 설정되지 않았습니다");
        }
        try {
            GeminiRequest body = new GeminiRequest(
                    List.of(new GeminiRequest.Content("user", List.of(new GeminiRequest.Part(userPrompt)))),
                    new GeminiRequest.SystemInstruction(List.of(new GeminiRequest.Part(systemInstruction))));

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

    private record GeminiRequest(List<Content> contents, SystemInstruction systemInstruction) {
        private record Content(String role, List<Part> parts) {}
        private record SystemInstruction(List<Part> parts) {}
        private record Part(String text) {}
    }

    private record GeminiResponse(List<Candidate> candidates) {
        private record Candidate(Content content) {}
        private record Content(List<Part> parts) {}
        private record Part(String text) {}
    }
}
