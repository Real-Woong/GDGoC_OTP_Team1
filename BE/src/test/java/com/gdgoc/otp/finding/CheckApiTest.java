package com.gdgoc.otp.finding;

import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.repository.PayslipRepository;
import com.gdgoc.otp.support.ApiTestSupport;
import com.gdgoc.otp.task.repository.UserTaskRepository;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 점검 API 테스트: 명세서 → 확인 → 점검 → 결과·할 일 흐름 전체.
 */
class CheckApiTest extends ApiTestSupport {

    @Autowired UserRepository userRepository;
    @Autowired PayslipRepository payslipRepository;
    @Autowired UserTaskRepository userTaskRepository;

    User chulsoo;
    Long payslipId;

    @BeforeEach
    void setUp() {
        chulsoo = userRepository.save(User.create("chulsoo"));
        userRepository.save(User.create("younghee"));

        // 실수령액이 10,000원 안 맞는 명세서
        Payslip payslip = Payslip.create(chulsoo, "2026-03", "테스트회사", null, null);
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.BASE_SALARY, 2_300_000, 0.99, "기본급"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.MEAL_ALLOWANCE, 200_000, 0.99, "식대"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.PAYMENT_TOTAL, 2_500_000, 0.99, "지급액계"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.DEDUCTION_TOTAL, 300_000, 0.99, "공제액계"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.NET_PAY, 2_190_000, 0.99, "차인지급액"));
        payslipId = payslipRepository.save(payslip).getId();
    }

    private void confirmAll() {
        payslipRepository.findById(payslipId).orElseThrow().getFields()
                .forEach(field -> field.confirm(field.getValue()));
    }

    private void setSme(Boolean isSme) {
        userRepository.findById(chulsoo.getId()).orElseThrow()
                .updateProfile(2001, LocalDate.of(2026, 3, 2), isSme);
    }

    @Test
    void 확인하지_않은_명세서는_점검할_수_없다() throws Exception {
        mockMvc.perform(post("/api/payslips/{id}/check", payslipId).header("X-User-Subject", "chulsoo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYSLIP_NOT_CONFIRMED"));
    }

    @Test
    void 중소기업_재직자는_감면_안내와_할일을_받는다() throws Exception {
        confirmAll();
        setSme(true);

        mockMvc.perform(post("/api/payslips/{id}/check", payslipId).header("X-User-Subject", "chulsoo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleVersion").value("2026-demo-v1"))
                .andExpect(jsonPath("$.ruleStatus").value("DRAFT"))
                // 실수령액 불일치(WARNING), 식대 안내(INFO), 감면 안내(WARNING)
                .andExpect(jsonPath("$.findings[?(@.ruleCode == 'NET_PAY_MISMATCH')]").exists())
                .andExpect(jsonPath("$.findings[?(@.ruleCode == 'MEAL_ALLOWANCE_NON_TAXABLE')]").exists())
                .andExpect(jsonPath("$.findings[?(@.ruleCode == 'SME_EMPLOYEE_TAX_REDUCTION')]").exists())
                .andExpect(jsonPath("$.findings[0].isEstimate").value(true))
                .andExpect(jsonPath("$.createdTasks.length()").value(1))
                .andExpect(jsonPath("$.createdTasks[0].templateKey").value("sme-reduction-application"))
                .andExpect(jsonPath("$.createdTasks[0].findingId").isNumber())
                // 시드의 기한 규칙이 TODO(미검증)라 기한은 비어 있다
                .andExpect(jsonPath("$.createdTasks[0].dueOn").isEmpty());
    }

    @Test
    void 다시_점검해도_할일은_중복으로_생기지_않는다() throws Exception {
        confirmAll();
        setSme(true);

        mockMvc.perform(post("/api/payslips/{id}/check", payslipId).header("X-User-Subject", "chulsoo"))
                .andExpect(jsonPath("$.createdTasks.length()").value(1));
        mockMvc.perform(post("/api/payslips/{id}/check", payslipId).header("X-User-Subject", "chulsoo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdTasks.length()").value(0));

        assertThat(userTaskRepository.findByUser_IdOrderByDueOnAscIdAsc(chulsoo.getId())).hasSize(1);
        // 결과 이력은 두 번치가 쌓인다
        mockMvc.perform(getAs("chulsoo", "/api/payslips/{id}/findings", payslipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void 중소기업_여부를_모르면_입력_요청_안내만() throws Exception {
        confirmAll();

        mockMvc.perform(post("/api/payslips/{id}/check", payslipId).header("X-User-Subject", "chulsoo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.findings[?(@.ruleCode == 'SME_EMPLOYEE_STATUS_UNKNOWN')]").exists())
                .andExpect(jsonPath("$.createdTasks.length()").value(0));
    }

    @Test
    void 규칙이_없는_연도는_404() throws Exception {
        Payslip old = Payslip.create(chulsoo, "2019-12", null, null, null);
        old.addField(PayslipField.fromOcr(PayslipFieldKey.BASE_SALARY, 1, 0.99, "기본급"));
        old.getFields().forEach(field -> field.confirm(field.getValue()));
        Long oldId = payslipRepository.save(old).getId();

        mockMvc.perform(post("/api/payslips/{id}/check", oldId).header("X-User-Subject", "chulsoo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAX_RULE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("2019년 점검 규칙이 아직 없습니다."));
    }

    @Test
    void 남의_명세서는_점검도_결과조회도_404() throws Exception {
        confirmAll();
        mockMvc.perform(post("/api/payslips/{id}/check", payslipId).header("X-User-Subject", "younghee"))
                .andExpect(status().isNotFound());
        mockMvc.perform(getAs("younghee", "/api/payslips/{id}/findings", payslipId))
                .andExpect(status().isNotFound());
    }
}
