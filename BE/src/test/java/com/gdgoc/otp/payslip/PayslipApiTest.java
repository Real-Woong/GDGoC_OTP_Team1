package com.gdgoc.otp.payslip;

import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.domain.UnmappedItem;
import com.gdgoc.otp.payslip.repository.PayslipRepository;
import com.gdgoc.otp.support.ApiTestSupport;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세서 조회·확인 API 테스트.
 * 업로드 API가 아직 없으므로(6단계) 테스트 데이터는 Repository로 직접 넣는다.
 */
class PayslipApiTest extends ApiTestSupport {

    @Autowired UserRepository userRepository;
    @Autowired PayslipRepository payslipRepository;

    Long payslipId;

    @BeforeEach
    void setUpPayslip() {
        User chulsoo = userRepository.save(User.create("chulsoo"));
        userRepository.save(User.create("younghee"));

        Payslip payslip = Payslip.create(chulsoo, "2026-03", "테스트회사",
                List.of(new UnmappedItem("명절 상여", 300_000)), List.of());
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.MEAL_ALLOWANCE, 20_000, 0.5, "중식대"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.BASE_SALARY, 2_500_000, 0.97, "기본급"));
        payslipId = payslipRepository.save(payslip).getId();
        payslipRepository.save(Payslip.create(chulsoo, "2026-04", null, null, null));
    }

    @Test
    void 내_명세서_목록() throws Exception {
        mockMvc.perform(getAs("chulsoo", "/api/payslips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].payPeriod").value("2026-04"));

        mockMvc.perform(getAs("younghee", "/api/payslips"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 상세는_필드를_화면순서로_준다() throws Exception {
        mockMvc.perform(getAs("chulsoo", "/api/payslips/{id}", payslipId))
                .andExpect(status().isOk())
                // 저장은 식대→기본급 순이었지만 응답은 enum 순서(기본급 먼저)
                .andExpect(jsonPath("$.fields[0].key").value("base_salary"))
                .andExpect(jsonPath("$.fields[0].label").value("기본급"))
                .andExpect(jsonPath("$.fields[1].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.unmappedItems[0].label").value("명절 상여"))
                .andExpect(jsonPath("$.fullyConfirmed").value(false));
    }

    @Test
    void 남의_명세서는_404() throws Exception {
        mockMvc.perform(getAs("younghee", "/api/payslips/{id}", payslipId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYSLIP_NOT_FOUND"));
    }

    @Test
    void 필드를_고쳐서_확인한다() throws Exception {
        String body = """
                { "fields": [
                    { "key": "base_salary",    "value": 2500000 },
                    { "key": "meal_allowance", "value": 200000 }
                ] }
                """;
        mockMvc.perform(patchJsonAs("chulsoo", body, "/api/payslips/{id}/fields", payslipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[1].value").value(200000))
                .andExpect(jsonPath("$.fields[1].status").value("OK"))
                .andExpect(jsonPath("$.fields[1].confidence").isEmpty())
                .andExpect(jsonPath("$.fields[1].userConfirmed").value(true))
                .andExpect(jsonPath("$.fullyConfirmed").value(true));
    }

    @Test
    void 모르는_필드코드는_400() throws Exception {
        mockMvc.perform(patchJsonAs("chulsoo", "{\"fields\":[{\"key\":\"salary\",\"value\":1}]}",
                        "/api/payslips/{id}/fields", payslipId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("알 수 없는 필드입니다: salary"));
    }

    @Test
    void 명세서에_없는_필드는_404() throws Exception {
        mockMvc.perform(patchJsonAs("chulsoo", "{\"fields\":[{\"key\":\"bonus\",\"value\":1}]}",
                        "/api/payslips/{id}/fields", payslipId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYSLIP_FIELD_NOT_FOUND"));
    }

    @Test
    void 음수_금액과_빈_목록은_400() throws Exception {
        mockMvc.perform(patchJsonAs("chulsoo", "{\"fields\":[{\"key\":\"base_salary\",\"value\":-1}]}",
                        "/api/payslips/{id}/fields", payslipId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fields[0].value"));
        mockMvc.perform(patchJsonAs("chulsoo", "{\"fields\":[]}", "/api/payslips/{id}/fields", payslipId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 남의_명세서는_확인할_수_없다() throws Exception {
        mockMvc.perform(patchJsonAs("younghee", "{\"fields\":[{\"key\":\"base_salary\",\"value\":1}]}",
                        "/api/payslips/{id}/fields", payslipId))
                .andExpect(status().isNotFound());
    }
}
