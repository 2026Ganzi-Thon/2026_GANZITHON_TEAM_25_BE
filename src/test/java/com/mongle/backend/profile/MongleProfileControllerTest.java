package com.mongle.backend.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongle.backend.support.AbstractIntegrationTest;
import com.mongle.backend.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 2026-11-18 — 온보딩(나를 닮은 특징·편안한 성향·장식) 저장 API. */
@Import(AuthTestSupport.class)
class MongleProfileControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthTestSupport authTestSupport;

    @Test
    void 토큰_없이_조회하면_401() throws Exception {
        mockMvc.perform(get("/mongle-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void 최초_조회시_전부_null이고_온보딩_미완료다() throws Exception {
        String token = authTestSupport.newUserToken();

        getProfile(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarityTrait").doesNotExist())
                .andExpect(jsonPath("$.comfortStyle").doesNotExist())
                .andExpect(jsonPath("$.accessory").doesNotExist())
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
    }

    @Test
    void 장식을_정하면_온보딩_완료로_바뀐다() throws Exception {
        String token = authTestSupport.newUserToken();

        patchProfile(token, "{\"accessory\":\"리본(머리)\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessory").value("리본(머리)"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    void 닮은_특징과_성향은_건너뛰어도_온보딩_완료에_영향이_없다() throws Exception {
        String token = authTestSupport.newUserToken();

        patchProfile(token, "{\"accessory\":\"없음\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarityTrait").doesNotExist())
                .andExpect(jsonPath("$.comfortStyle").doesNotExist())
                .andExpect(jsonPath("$.accessory").value("없음"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    void 장식_없음은_null과_다르게_실제_값으로_저장된다() throws Exception {
        String token = authTestSupport.newUserToken();

        patchProfile(token, "{\"accessory\":\"모자\"}").andExpect(status().isOk());
        // accessory를 아예 안 보내면(null) 기존 값(모자) 유지 — "없음"으로 바꾸려면 명시적으로 보내야 함
        patchProfile(token, "{\"similarityTrait\":\"잘 웃기\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessory").value("모자"));

        patchProfile(token, "{\"accessory\":\"없음\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessory").value("없음"));

        getProfile(token).andExpect(jsonPath("$.accessory").value("없음"));
    }

    @Test
    void 부분_수정하면_나머지_필드는_그대로_유지된다() throws Exception {
        String token = authTestSupport.newUserToken();

        patchProfile(token, "{\"similarityTrait\":\"멍하니 있기\",\"comfortStyle\":\"가끔 장난스러움\"}")
                .andExpect(status().isOk());

        patchProfile(token, "{\"accessory\":\"방울(목)\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarityTrait").value("멍하니 있기"))
                .andExpect(jsonPath("$.comfortStyle").value("가끔 장난스러움"))
                .andExpect(jsonPath("$.accessory").value("방울(목)"));
    }

    @Test
    void 두_유저의_프로필은_서로_섞이지_않는다() throws Exception {
        String tokenA = authTestSupport.newUserToken();
        String tokenB = authTestSupport.newUserToken();

        patchProfile(tokenA, "{\"accessory\":\"작은 꽃(머리)\"}").andExpect(status().isOk());

        getProfile(tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessory").doesNotExist())
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"장화\"", "\"리본\"", "\"\"", "\"none\""})
    void 선택지에_없는_장식은_거부된다(String value) throws Exception {
        String token = authTestSupport.newUserToken();

        patchProfile(token, "{\"accessory\":" + value + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("accessory"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"명랑함\"", "\"아무거나\""})
    void 선택지에_없는_닮은_특징은_거부된다(String value) throws Exception {
        String token = authTestSupport.newUserToken();

        patchProfile(token, "{\"similarityTrait\":" + value + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("similarityTrait"));
    }

    private ResultActions getProfile(String token) throws Exception {
        return mockMvc.perform(get("/mongle-profile").header("Authorization", "Bearer " + token));
    }

    private ResultActions patchProfile(String token, String body) throws Exception {
        return mockMvc.perform(patch("/mongle-profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
