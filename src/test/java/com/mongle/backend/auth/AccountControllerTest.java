package com.mongle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongle.backend.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 아이디·비밀번호 가입/중복확인/로그인(2026-11-15 팀 결정). 실제 MySQL 위에서 HTTP 계층부터 돈다.
 * 호출 제한이 접속 IP 기준이라 요청마다 다른 IP로 보내서(newClient) 테스트끼리 서로의 한도를 쓰지 않게 한다.
 */
class AccountControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "abc123!x";
    private static final AtomicInteger CLIENT_SEQ = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static RequestPostProcessor newClient() {
        int n = CLIENT_SEQ.incrementAndGet();
        return clientAt("10.30." + ((n >> 8) & 255) + "." + (n & 255));
    }

    private static RequestPostProcessor clientAt(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** 규칙(소문자+숫자 6~20자)에 맞는 새 아이디. */
    private static String newLoginId() {
        return "u1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private ResultActions signup(String name, String loginId, String password, Object terms, Object privacy) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("loginId", loginId);
        body.put("password", password);
        body.put("termsAgreed", terms);
        body.put("privacyAgreed", privacy);
        return mockMvc.perform(post("/auth/signup").with(newClient())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String signupOk(String loginId) throws Exception {
        String response = signup("홍길동", loginId, PASSWORD, true, true)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("userId").asText();
    }

    private ResultActions login(String loginId, String password, RequestPostProcessor client) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginId", loginId);
        body.put("password", password);
        return mockMvc.perform(post("/auth/login").with(client)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    // ---------- 가입 ----------

    @Test
    void 가입하면_201과_userId_onboardingCompleted_false를_돌려주고_토큰도_비밀번호도_담지_않는다() throws Exception {
        signup("홍길동", newLoginId(), PASSWORD, true, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(notNullValue()))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    void 비밀번호는_BCrypt_해시로만_저장되고_이름은_앞뒤_공백을_뗀다() throws Exception {
        String loginId = newLoginId();
        signup("  홍길동  ", loginId, PASSWORD, true, true).andExpect(status().isCreated());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT password_hash, name, device_id, terms_agreed_at, privacy_agreed_at FROM app_user WHERE login_id = ?", loginId);
        assertThat((String) row.get("password_hash")).startsWith("$2").isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(row.get("name")).isEqualTo("홍길동");
        assertThat(row.get("device_id")).as("가입 유저는 기기 ID가 없다").isNull();
        assertThat(row.get("terms_agreed_at")).as("이용약관 동의 시각").isNotNull();
        assertThat(row.get("privacy_agreed_at")).as("개인정보 처리방침 동의 시각").isNotNull();
    }

    static Stream<Arguments> invalidSignups() {
        // (설명, 이름, 아이디, 비밀번호, 문제 필드) — null이면 그 칸은 유효한 값으로 채운다
        return Stream.of(
                Arguments.of("이름이 공백뿐", "   ", null, null, "name"),
                Arguments.of("이름이 21자", "가".repeat(21), null, null, "name"),
                Arguments.of("이름에 제어문자", "홍\t길동", null, null, "name"),
                Arguments.of("아이디 5자", null, "ab123", null, "loginId"),
                Arguments.of("아이디 21자", null, "a1" + "b".repeat(19), null, "loginId"),
                Arguments.of("아이디에 대문자", null, "Abc123", null, "loginId"),
                Arguments.of("아이디가 숫자뿐", null, "123456", null, "loginId"),
                Arguments.of("아이디가 영문뿐", null, "abcdef", null, "loginId"),
                Arguments.of("아이디에 밑줄", null, "abc_123", null, "loginId"),
                Arguments.of("비밀번호 5자", null, null, "a1!bc", "password"),
                Arguments.of("비밀번호에 특수기호 없음", null, null, "abc12345", "password"),
                Arguments.of("비밀번호에 숫자 없음", null, null, "abcdef!!", "password"),
                Arguments.of("비밀번호에 영문 없음", null, null, "123456!!", "password"),
                Arguments.of("비밀번호에 한글", null, null, "abc123!가", "password"),
                Arguments.of("비밀번호에 공백", null, null, "abc 123!", "password"),
                Arguments.of("비밀번호 65자", null, null, "a1!" + "b".repeat(62), "password"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSignups")
    void 규칙에_어긋나면_400과_문제_필드를_돌려준다(String label, String name, String loginId, String password, String field) throws Exception {
        signup(name != null ? name : "홍길동", loginId != null ? loginId : newLoginId(),
                password != null ? password : PASSWORD, true, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value(field))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    void 약관에_동의하지_않으면_400이다() throws Exception {
        signup("홍길동", newLoginId(), PASSWORD, false, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("termsAgreed"));
        signup("홍길동", newLoginId(), PASSWORD, true, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("privacyAgreed"));
        signup("홍길동", newLoginId(), PASSWORD, null, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("termsAgreed"));
    }

    @Test
    void 이미_있는_아이디로_가입하면_409_CONFLICT와_loginId_필드다() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        signup("다른사람", loginId, PASSWORD, true, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.field").value("loginId"));
    }

    // ---------- 중복확인 ----------

    @Test
    void 중복확인은_없는_아이디면_true_있는_아이디면_false다() throws Exception {
        String loginId = newLoginId();
        mockMvc.perform(get("/auth/check-id").param("loginId", loginId).with(newClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        signupOk(loginId);

        mockMvc.perform(get("/auth/check-id").param("loginId", loginId).with(newClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 중복확인에_형식이_틀린_아이디나_빈_값을_보내면_400이다() throws Exception {
        mockMvc.perform(get("/auth/check-id").param("loginId", "Abc123").with(newClient()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("loginId"));
        mockMvc.perform(get("/auth/check-id").with(newClient()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("loginId"));
    }

    // ---------- 로그인 ----------

    @Test
    void 가입한_아이디로_로그인하면_토큰과_이름을_받고_그_토큰으로_보호된_API를_쓸_수_있다() throws Exception {
        String loginId = newLoginId();
        String userId = signupOk(loginId);

        String response = login(loginId, PASSWORD, newClient())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.expiresAt").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/sessions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entryType\":\"TALK\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 틀린_비밀번호와_없는_아이디는_응답이_완전히_같다_계정_존재_여부를_알_수_없다() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        MvcResult wrongPassword = login(loginId, "wrong1!x", newClient()).andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknownId = login(newLoginId(), PASSWORD, newClient()).andExpect(status().isUnauthorized()).andReturn();

        JsonNode a = objectMapper.readTree(wrongPassword.getResponse().getContentAsString());
        JsonNode b = objectMapper.readTree(unknownId.getResponse().getContentAsString());
        assertThat(a).isEqualTo(b);
        assertThat(a.get("error").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void 형식이_이상한_아이디나_너무_긴_비밀번호로_로그인해도_500이나_400이_아니라_같은_401이다() throws Exception {
        login("ABC123", PASSWORD, newClient()).andExpect(status().isUnauthorized());
        login("àbc123", PASSWORD, newClient()).andExpect(status().isUnauthorized());
        login(newLoginId(), "가".repeat(60), newClient()).andExpect(status().isUnauthorized()); // 한글 60자 = 180바이트
        login(newLoginId(), "a1!" + "b".repeat(90), newClient()).andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인_요청에_아이디나_비밀번호가_비어_있으면_400이다() throws Exception {
        login("", PASSWORD, newClient()).andExpect(status().isBadRequest());
        login(newLoginId(), "", newClient()).andExpect(status().isBadRequest());
    }

    // ---------- 로그인 응답의 온보딩 완료 여부 ----------

    private String loginToken(String loginId) throws Exception {
        String response = login(loginId, PASSWORD, newClient()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private void patchProfile(String token, String json) throws Exception {
        mockMvc.perform(patch("/mongle-profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void 프로필을_만든_적_없는_계정은_로그인_응답이_onboardingCompleted_false이고_로그인이_프로필_row를_만들지_않는다() throws Exception {
        String loginId = newLoginId();
        String userId = signupOk(loginId);

        login(loginId, PASSWORD, newClient())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(false));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mongle_profile WHERE user_id = ?", Integer.class, userId);
        assertThat(rows).as("로그인만 했을 때 빈 프로필 row가 생기면 안 된다").isZero();
    }

    @Test
    void 장식을_저장한_뒤_다시_로그인하면_onboardingCompleted_true다() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);
        patchProfile(loginToken(loginId), "{\"accessory\":\"없음\"}");

        login(loginId, PASSWORD, newClient())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    void 닮은_특징과_성향만_저장하고_장식이_없으면_로그인_응답은_onboardingCompleted_false다() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);
        patchProfile(loginToken(loginId), "{\"similarityTrait\":\"잘 웃기\",\"comfortStyle\":\"부드럽고 다정함\"}");

        login(loginId, PASSWORD, newClient())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
    }

    // ---------- 호출 제한 ----------

    @Test
    void 한_아이디로_10번_틀리면_올바른_비밀번호로도_429이고_다른_아이디는_영향이_없다() throws Exception {
        String target = newLoginId();
        String other = newLoginId();
        signupOk(target);
        signupOk(other);

        for (int i = 0; i < 10; i++) {
            login(target, "wrong1!x", newClient()).andExpect(status().isUnauthorized());
        }

        login(target, PASSWORD, newClient()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
        login(other, PASSWORD, newClient()).andExpect(status().isOk());
    }

    @Test
    void 로그인에_성공하면_실패_횟수가_초기화된다() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        for (int i = 0; i < 9; i++) {
            login(loginId, "wrong1!x", newClient()).andExpect(status().isUnauthorized());
        }
        login(loginId, PASSWORD, newClient()).andExpect(status().isOk());
        for (int i = 0; i < 9; i++) {
            login(loginId, "wrong1!x", newClient()).andExpect(status().isUnauthorized()); // 초기화 안 됐으면 여기서 429
        }
    }

    @Test
    void 같은_IP에서_로그인을_60번_넘게_부르면_429다() throws Exception {
        RequestPostProcessor sameClient = clientAt("10.99.0.1");
        for (int i = 0; i < 60; i++) {
            login(newLoginId(), PASSWORD, sameClient).andExpect(status().isUnauthorized());
        }
        login(newLoginId(), PASSWORD, sameClient).andExpect(status().isTooManyRequests());
    }

    @Test
    void 같은_IP에서_중복확인을_120번_넘게_부르면_429다() throws Exception {
        RequestPostProcessor sameClient = clientAt("10.99.0.2");
        for (int i = 0; i < 120; i++) {
            mockMvc.perform(get("/auth/check-id").param("loginId", "ab1234").with(sameClient)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/auth/check-id").param("loginId", "ab1234").with(sameClient))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 같은_IP에서_가입을_30번_넘게_부르면_429다() throws Exception {
        RequestPostProcessor sameClient = clientAt("10.99.0.3");
        for (int i = 0; i < 30; i++) {
            Map<String, Object> body = Map.of("name", "홍길동", "loginId", newLoginId(), "password", PASSWORD,
                    "termsAgreed", true, "privacyAgreed", true);
            mockMvc.perform(post("/auth/signup").with(sameClient).contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated());
        }
        Map<String, Object> body = Map.of("name", "홍길동", "loginId", newLoginId(), "password", PASSWORD,
                "termsAgreed", true, "privacyAgreed", true);
        mockMvc.perform(post("/auth/signup").with(sameClient).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isTooManyRequests());
    }
}
