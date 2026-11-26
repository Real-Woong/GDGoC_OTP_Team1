package com.gdgoc.otp.user;

import com.gdgoc.otp.support.ApiTestSupport;
import com.gdgoc.otp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiTest extends ApiTestSupport {

    @Autowired UserRepository userRepository;

    @Test
    void 헤더가_없으면_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 처음_보는_사용자는_자동_가입되고_두번째는_같은_사용자() throws Exception {
        mockMvc.perform(getAs("new-user", "/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.hasConsented").value(false))
                .andExpect(jsonPath("$.birthYear").isEmpty());
        mockMvc.perform(getAs("new-user", "/api/users/me")).andExpect(status().isOk());

        assertThat(userRepository.findByAuthSubject("new-user")).isPresent();
        assertThat(userRepository.findAll()).filteredOn(u -> u.getAuthSubject().equals("new-user")).hasSize(1);
    }

    @Test
    void 내_정보를_수정한다() throws Exception {
        mockMvc.perform(putJsonAs("chulsoo",
                        "{\"birthYear\":2001,\"joinedOn\":\"2026-03-02\",\"isSmeEmployee\":true}",
                        "/api/users/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthYear").value(2001))
                .andExpect(jsonPath("$.joinedOn").value("2026-03-02"))
                .andExpect(jsonPath("$.isSmeEmployee").value(true));
    }

    @Test
    void 잘못된_출생연도는_400과_필드사유() throws Exception {
        mockMvc.perform(putJsonAs("chulsoo", "{\"birthYear\":1800}", "/api/users/me/profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("birthYear"));
    }

    @Test
    void 약관에_동의한다() throws Exception {
        mockMvc.perform(postJsonAs("chulsoo", "{\"termsVersion\":\"2026-11-v1\"}", "/api/users/me/terms-agreement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConsented").value(true))
                .andExpect(jsonPath("$.termsVersion").value("2026-11-v1"))
                .andExpect(jsonPath("$.consentedAt").isNotEmpty());
    }
}
