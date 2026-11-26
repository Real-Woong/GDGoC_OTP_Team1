package com.gdgoc.otp.global.config;

import com.gdgoc.otp.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 브라우저가 보내는 CORS 요청을 흉내 내 허용·거부를 확인한다.
 * (Origin 헤더 = "이 요청을 보낸 프론트 주소". 브라우저가 자동으로 붙인다.)
 */
class CorsTest extends ApiTestSupport {

    @Test
    void 허용된_프론트의_preflight는_통과한다() throws Exception {
        mockMvc.perform(options("/api/tasks/1/status")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "content-type, x-user-subject"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    void 허용된_프론트의_실제_요청에도_허용_헤더가_붙는다() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Origin", "http://localhost:5173")
                        .header("X-User-Subject", "chulsoo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void 허용되지_않은_출처는_거부된다() throws Exception {
        mockMvc.perform(options("/api/users/me")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
