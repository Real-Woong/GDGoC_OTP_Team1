package com.gdgoc.otp.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예외가 공통 JSON 에러 응답으로 바뀌는지 확인한다.
 *
 * <p>DB나 스프링 전체를 띄우지 않는 가벼운 테스트다. {@code MockMvcBuilders.standaloneSetup}으로
 * 테스트 전용 컨트롤러 하나와 GlobalExceptionHandler만 연결해서, 가짜 HTTP 요청을 보낸다.</p>
 */
class GlobalExceptionHandlerTest {

    /** 일부러 예외를 던지는 테스트용 컨트롤러. */
    @RestController
    static class FailingController {

        record CreateRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
        }

        @GetMapping("/test/business")
        String business() {
            throw new BusinessException(ErrorCode.PAYSLIP_NOT_FOUND);
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/test/bug")
        String bug() {
            throw new IllegalStateException("내부 정보가 담긴 메시지");
        }

        @GetMapping("/test/items/{id}")
        String item(@PathVariable Long id) {
            return "ok";
        }

        @PostMapping("/test/create")
        String create(@Valid @RequestBody CreateRequest request) {
            return "ok";
        }
    }

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 비즈니스_예외는_ErrorCode의_상태와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYSLIP_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("명세서를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 검증_실패는_필드별_사유를_돌려준다() throws Exception {
        mockMvc.perform(post("/test/create").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("이름은 필수입니다."));
    }

    @Test
    void JSON_문법_오류는_400() throws Exception {
        mockMvc.perform(post("/test/create").contentType(MediaType.APPLICATION_JSON).content("{깨진 json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 경로변수_타입이_틀리면_400() throws Exception {
        mockMvc.perform(get("/test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void DB_제약_위반은_409이고_내부정보를_숨긴다() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("이미 처리되었거나 다른 데이터와 충돌합니다."));
    }

    @Test
    void 예상못한_예외는_500이고_내부메시지를_숨긴다() throws Exception {
        mockMvc.perform(get("/test/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
