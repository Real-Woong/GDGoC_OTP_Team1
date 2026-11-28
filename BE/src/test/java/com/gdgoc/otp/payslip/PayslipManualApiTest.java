package com.gdgoc.otp.payslip;

import com.gdgoc.otp.support.ApiTestSupport;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 직접 입력(OCR 없이 빈 명세서 만들기) API 테스트. */
class PayslipManualApiTest extends ApiTestSupport {

    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User agreed = User.create("agreed");
        agreed.agreeToTerms("2026-11-v1", Instant.now());
        userRepository.save(agreed);
        userRepository.save(User.create("not-agreed"));
    }

    private MockHttpServletRequestBuilder manual(String subject, String body) {
        return post("/api/payslips/manual").header("X-User-Subject", subject)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void 빈_명세서가_14칸_MISSING으로_만들어진다() throws Exception {
        mockMvc.perform(manual("agreed", "{\"payPeriod\":\"2026-03\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payPeriod").value("2026-03"))
                .andExpect(jsonPath("$.fields", hasSize(14)))
                .andExpect(jsonPath("$.fields[*].status", everyItem(is("MISSING"))))
                .andExpect(jsonPath("$.fullyConfirmed").value(false));
    }

    @Test
    void 만든_뒤_확인화면과_같은_API로_값을_채운다() throws Exception {
        String created = mockMvc.perform(manual("agreed", "{\"payPeriod\":\"2026-03\"}"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/payslips/{id}/fields", id).header("X-User-Subject", "agreed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[{\"key\":\"base_salary\",\"value\":2400000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.key=='base_salary')].value").value(2400000))
                .andExpect(jsonPath("$.fields[?(@.key=='base_salary')].status").value("OK"));
    }

    @Test
    void 약관_미동의면_403() throws Exception {
        mockMvc.perform(manual("not-agreed", "{\"payPeriod\":\"2026-03\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_AGREED"));
    }

    @Test
    void 귀속월이_없거나_형식이_틀리면_400() throws Exception {
        mockMvc.perform(manual("agreed", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(manual("agreed", "{\"payPeriod\":\"2026-13\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("payPeriod"));
    }

    @Test
    void 같은_달이_이미_있으면_경고() throws Exception {
        mockMvc.perform(manual("agreed", "{\"payPeriod\":\"2026-03\"}"));
        mockMvc.perform(manual("agreed", "{\"payPeriod\":\"2026-03\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0].reason").value("같은 귀속월(2026-03) 명세서가 이미 있습니다."));
    }
}
