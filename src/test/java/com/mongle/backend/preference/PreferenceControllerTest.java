package com.mongle.backend.preference;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API_명세서_v1 6장 — 지금까지 유일하게 통합테스트가 없던 모듈(성준님 담당 A 영역). */
@Import(AuthTestSupport.class)
class PreferenceControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthTestSupport authTestSupport;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void 토큰_없이_조회하면_401() throws Exception {
        mockMvc.perform(get("/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void 최초_조회시_기본값_3개가_true로_자동_생성된다() throws Exception {
        String token = authTestSupport.newUserToken();

        mockMvc.perform(get("/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soundEnabled").value(true))
                .andExpect(jsonPath("$.hapticsEnabled").value(true))
                .andExpect(jsonPath("$.animationEnabled").value(true))
                .andExpect(jsonPath("$.natureSound").value("off"))
                .andExpect(jsonPath("$.natureSoundVolume").value(50))
                .andExpect(jsonPath("$.natureSoundAutoPlayEnabled").value(false));
    }

    @Test
    void 부분_수정하면_나머지_필드는_그대로_유지된다() throws Exception {
        String token = authTestSupport.newUserToken();

        // soundEnabled만 false로 — hapticsEnabled/animationEnabled는 요청에 아예 안 실음
        mockMvc.perform(patch("/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soundEnabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soundEnabled").value(false))
                .andExpect(jsonPath("$.hapticsEnabled").value(true))
                .andExpect(jsonPath("$.animationEnabled").value(true));

        // 다시 조회해도 유지되는지(응답 값만 보고 끝내지 않고 DB에 실제로 박혔는지 확인)
        mockMvc.perform(get("/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soundEnabled").value(false))
                .andExpect(jsonPath("$.hapticsEnabled").value(true));
    }

    @Test
    void 두_유저의_설정은_서로_섞이지_않는다() throws Exception {
        String tokenA = authTestSupport.newUserToken();
        String tokenB = authTestSupport.newUserToken();

        mockMvc.perform(patch("/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soundEnabled\": false, \"hapticsEnabled\": false}"))
                .andExpect(status().isOk());

        // B는 A를 건드린 적 없으니 여전히 기본값(true)이어야 한다 — 교차 오염 확인
        mockMvc.perform(get("/preferences").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soundEnabled").value(true))
                .andExpect(jsonPath("$.hapticsEnabled").value(true));
    }

    @Test
    void 타입이_안_맞는_값을_보내면_명세서_포맷의_400을_반환한다() throws Exception {
        String token = authTestSupport.newUserToken();

        // soundEnabled는 Boolean인데 문자열을 보냄 — Bean Validation 전에 역직렬화 단계에서 터지는 케이스
        mockMvc.perform(patch("/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soundEnabled\": \"yes\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void databaseDefaultsPreserveTheLegacySettings() throws Exception {
        var user = authTestSupport.newUser();
        jdbcTemplate.update("""
                INSERT INTO app_preference (user_id, sound_enabled, haptics_enabled, animation_enabled)
                VALUES (?, FALSE, TRUE, FALSE)
                """, user.userId());

        getPreferences(user.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soundEnabled").value(false))
                .andExpect(jsonPath("$.hapticsEnabled").value(true))
                .andExpect(jsonPath("$.animationEnabled").value(false))
                .andExpect(jsonPath("$.natureSound").value("off"))
                .andExpect(jsonPath("$.natureSoundVolume").value(50))
                .andExpect(jsonPath("$.natureSoundAutoPlayEnabled").value(false));
    }

    @Test
    void natureSoundAndVolumePersistIndependentlyOfExistingSettings() throws Exception {
        String token = authTestSupport.newUserToken();

        patchPreferences(token, """
                {"natureSound":"rain","natureSoundVolume":100}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soundEnabled").value(true))
                .andExpect(jsonPath("$.natureSound").value("rain"))
                .andExpect(jsonPath("$.natureSoundVolume").value(100));

        patchPreferences(token, """
                {"soundEnabled":false,"natureSound":null,"natureSoundVolume":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.natureSound").value("rain"))
                .andExpect(jsonPath("$.natureSoundVolume").value(0));

        patchPreferences(token, """
                {"natureSound":"off","natureSoundVolume":null}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.natureSound").value("off"))
                .andExpect(jsonPath("$.natureSoundVolume").value(0));

        patchPreferences(token, """
                {"animationEnabled":false}
                """).andExpect(status().isOk());

        getPreferences(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soundEnabled").value(false))
                .andExpect(jsonPath("$.hapticsEnabled").value(true))
                .andExpect(jsonPath("$.animationEnabled").value(false))
                .andExpect(jsonPath("$.natureSound").value("off"))
                .andExpect(jsonPath("$.natureSoundVolume").value(0));
    }

    @Test
    void usersHaveSeparateNatureSoundSettings() throws Exception {
        String firstToken = authTestSupport.newUserToken();
        String secondToken = authTestSupport.newUserToken();

        patchPreferences(firstToken, """
                {"natureSound":"insects","natureSoundVolume":80,"natureSoundAutoPlayEnabled":true}
                """).andExpect(status().isOk());

        getPreferences(secondToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.natureSound").value("off"))
                .andExpect(jsonPath("$.natureSoundVolume").value(50))
                .andExpect(jsonPath("$.natureSoundAutoPlayEnabled").value(false));
    }

    @Test
    void autoPlayPreservesTheSavedSoundAndVolume() throws Exception {
        String token = authTestSupport.newUserToken();

        patchPreferences(token, """
                {"natureSound":"rain","natureSoundVolume":35}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.natureSoundAutoPlayEnabled").value(false));

        patchPreferences(token, """
                {"natureSoundAutoPlayEnabled":true}
                """).andExpect(status().isOk());
        patchPreferences(token, """
                {"natureSoundAutoPlayEnabled":null,"natureSoundVolume":45}
                """).andExpect(status().isOk());
        patchPreferences(token, """
                {"hapticsEnabled":false}
                """).andExpect(status().isOk());

        getPreferences(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.natureSound").value("rain"))
                .andExpect(jsonPath("$.natureSoundVolume").value(45))
                .andExpect(jsonPath("$.natureSoundAutoPlayEnabled").value(true));

        patchPreferences(token, """
                {"natureSoundAutoPlayEnabled":false}
                """).andExpect(status().isOk());

        getPreferences(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.natureSound").value("rain"))
                .andExpect(jsonPath("$.natureSoundVolume").value(45))
                .andExpect(jsonPath("$.natureSoundAutoPlayEnabled").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "\"true\"", "[]"})
    void nonBooleanAutoPlayRejectsTheEntirePatch(String value) throws Exception {
        assertRejectedPatch(
                "{\"natureSound\":\"waves\",\"natureSoundAutoPlayEnabled\":" + value + "}",
                "natureSoundAutoPlayEnabled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"RAIN\"", "\"unknown\"", "\"\"", "\"rain \"", "1"})
    void invalidNatureSoundRejectsTheEntirePatch(String value) throws Exception {
        assertRejectedPatch("{\"natureSound\":" + value + ",\"natureSoundVolume\":72}", "natureSound");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "101", "50.5", "\"50\"", "true", "2147483648"})
    void invalidNatureSoundVolumeRejectsTheEntirePatch(String value) throws Exception {
        assertRejectedPatch("{\"natureSound\":\"waves\",\"natureSoundVolume\":" + value + "}", "natureSoundVolume");
    }

    private void assertRejectedPatch(String body, String field) throws Exception {
        String token = authTestSupport.newUserToken();
        patchPreferences(token, """
                {"natureSound":"wind","natureSoundVolume":25}
                """).andExpect(status().isOk());

        patchPreferences(token, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value(field));

        getPreferences(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.natureSound").value("wind"))
                .andExpect(jsonPath("$.natureSoundVolume").value(25))
                .andExpect(jsonPath("$.natureSoundAutoPlayEnabled").value(false));
    }

    private ResultActions getPreferences(String token) throws Exception {
        return mockMvc.perform(get("/preferences").header("Authorization", "Bearer " + token));
    }

    private ResultActions patchPreferences(String token, String body) throws Exception {
        return mockMvc.perform(patch("/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
