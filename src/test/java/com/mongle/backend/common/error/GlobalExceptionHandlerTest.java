package com.mongle.backend.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    @Test
    void 제약_위반은_내부_예외_문구_대신_사용자용_검증_오류를_보낸다() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new ValidationTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/validation-test"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "error": "VALIDATION_ERROR",
                          "message": "요청 형식이 올바르지 않습니다",
                          "field": null
                        }
                        """, JsonCompareMode.STRICT));
    }

    @RestController
    static class ValidationTestController {
        @GetMapping("/validation-test")
        void validate() {
            throw new ConstraintViolationException("internalLookup.input: invalid", Set.of());
        }
    }
}
