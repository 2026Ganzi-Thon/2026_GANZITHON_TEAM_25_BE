package com.mongle.backend.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongle.backend.support.AbstractIntegrationTest;
import com.mongle.backend.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 기억 요약 초안은 Gemini 출력을 그대로 draft_summary VARCHAR(300)에 넣는다. 사용자가 승인·수정하는
 * 요약에는 300자 검증이 있지만 Gemini 초안에는 없어서, 모델이 길게 답하면 DB 오류로 500이 날 수 있었다.
 * 요약기를 목으로 바꿔 "모델이 이상한 값을 돌려주는" 상황을 비용 없이 재현한다.
 */
@Import(AuthTestSupport.class)
class MemoryPreviewSummaryGuardTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthTestSupport authTestSupport;
    @MockitoBean private MemorySummarizer memorySummarizer;

    private String startSession(String token) throws Exception {
        String response = mockMvc.perform(post("/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryType\":\"TALK\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("sessionId").asText();
    }

    private String previewDraft(String summarizerResult) throws Exception {
        String token = authTestSupport.newUserToken();
        String sessionId = startSession(token);
        when(memorySummarizer.summarize(anyString())).thenReturn(summarizerResult);

        String response = mockMvc.perform(post("/memories/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("draftSummary").asText();
    }

    @Test
    void 모델이_300자를_넘는_요약을_돌려줘도_500이_아니라_300자_이내로_자른다() throws Exception {
        String draft = previewDraft("가".repeat(450));

        assertThat(draft.length()).isLessThanOrEqualTo(300);
        assertThat(draft).isNotBlank();
    }

    @Test
    void 정확히_300자인_요약은_그대로_둔다() throws Exception {
        String exactly300 = "나".repeat(300);

        assertThat(previewDraft(exactly300)).isEqualTo(exactly300);
    }

    @Test
    void 모델이_빈_문자열이나_공백만_돌려주면_기본_문구로_대체한다() throws Exception {
        assertThat(previewDraft("   \n  ")).isEqualTo("오늘의 대화");
    }
}
