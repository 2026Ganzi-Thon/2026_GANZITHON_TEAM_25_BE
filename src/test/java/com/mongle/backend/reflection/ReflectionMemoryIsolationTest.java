package com.mongle.backend.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongle.backend.memory.ApprovedMemory;
import com.mongle.backend.memory.ApprovedMemoryRepository;
import com.mongle.backend.reflection.dto.ReflectionRequest;
import com.mongle.backend.reflection.gemini.GeminiClient;
import com.mongle.backend.support.AbstractIntegrationTest;
import com.mongle.backend.support.AuthTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(AuthTestSupport.class)
class ReflectionMemoryIsolationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthTestSupport authTestSupport;
    @Autowired private ApprovedMemoryRepository approvedMemoryRepository;
    @MockitoBean(enforceOverride = true) private GeminiClient geminiClient;

    @Test
    void 타인의_기억_ID를_함께_보내도_내가_승인한_기억만_프롬프트에_들어간다() throws Exception {
        var user = authTestSupport.newUser();
        var otherUser = authTestSupport.newUser();
        var ownMemory = approvedMemoryRepository.save(
                ApprovedMemory.approve(user.userId(), "친구가 내 이야기를 끝까지 들어줬다"));
        var otherMemory = approvedMemoryRepository.save(
                ApprovedMemory.approve(otherUser.userId(), "동료와 갑자기 여행을 취소했다"));
        String authorization = "Bearer " + user.accessToken();
        String sessionResponse = mockMvc.perform(post("/sessions")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entryType": "TALK"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String sessionId = objectMapper.readTree(sessionResponse).get("sessionId").asText();
        var request = new ReflectionRequest("오늘 그 일이 다시 생각났어",
                List.of(ownMemory.getMemoryId(), otherMemory.getMemoryId()));
        String reply = "다시 떠올라서 마음이 복잡했겠어.";
        when(geminiClient.generateReflection(anyString(), anyString()))
                .thenReturn(new GeminiClient.ReflectionReply(reply, false));

        mockMvc.perform(post("/sessions/{sessionId}/reflections", sessionId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(reply));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateReflection(anyString(), prompt.capture());
        assertThat(prompt.getValue())
                .contains(ownMemory.getApprovedSummary())
                .doesNotContain(otherMemory.getApprovedSummary());
    }
}
