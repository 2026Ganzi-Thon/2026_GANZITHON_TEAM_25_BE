package com.mongle.backend.reflection.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Gemini가 구조화 JSON 계약을 어겨도(깨진 JSON·빈 reply) 500이 아니라 폴백으로 떨어지는지 고정한다. */
class GeminiClientParsingTest {

    private final GeminiClient client = new GeminiClient(new GeminiProperties(), new ObjectMapper());

    @Test
    void 정상_JSON이면_reply와_riskDetected를_파싱한다() {
        GeminiClient.ReflectionReply parsed =
                client.parseReflectionReply("{\"reply\":\"힘들었겠다\",\"riskDetected\":true}");

        assertThat(parsed.reply()).isEqualTo("힘들었겠다");
        assertThat(parsed.riskDetected()).isTrue();
    }

    @Test
    void 깨진_JSON이면_GeminiCallException() {
        assertThatThrownBy(() -> client.parseReflectionReply("{not json"))
                .isInstanceOf(GeminiCallException.class);
    }

    @Test
    void reply가_비어_있으면_GeminiCallException() {
        assertThatThrownBy(() -> client.parseReflectionReply("{\"reply\":\"  \",\"riskDetected\":false}"))
                .isInstanceOf(GeminiCallException.class);
    }

    @Test
    void reply_필드가_없으면_GeminiCallException() {
        assertThatThrownBy(() -> client.parseReflectionReply("{\"riskDetected\":false}"))
                .isInstanceOf(GeminiCallException.class);
    }
}
