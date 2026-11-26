package com.gdgoc.otp.payslip;

import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.repository.PayslipRepository;
import com.gdgoc.otp.support.ApiTestSupport;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.Instant;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세서 업로드 API 테스트. 가짜 OCR(FakeOcrClient)이 고정 샘플을 돌려준다.
 */
class PayslipUploadApiTest extends ApiTestSupport {

    static final byte[] PNG = UploadedFileValidatorTest.PNG;

    @Autowired UserRepository userRepository;
    @Autowired PayslipRepository payslipRepository;

    @BeforeEach
    void setUp() {
        User agreed = User.create("agreed");
        agreed.agreeToTerms("2026-11-v1", Instant.now());
        userRepository.save(agreed);
        userRepository.save(User.create("not-agreed"));
    }

    private MockMultipartHttpServletRequestBuilder upload(String subject, byte[] content) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/payslips")
                .file(new MockMultipartFile("file", "payslip.png", "image/png", content));
        builder.header("X-User-Subject", subject);
        return builder;
    }

    @Test
    void 업로드하면_확인화면용_상세가_201로_온다() throws Exception {
        mockMvc.perform(upload("agreed", PNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.payPeriod").value("2026-03"))
                .andExpect(jsonPath("$.companyName").value("샘플주식회사"))
                .andExpect(jsonPath("$.fields.length()").value(14))
                .andExpect(jsonPath("$.fields[?(@.key == 'meal_allowance')].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.fields[?(@.key == 'bonus')].status").value("MISSING"))
                .andExpect(jsonPath("$.unmappedItems[0].label").value("명절 상여"))
                .andExpect(jsonPath("$.warnings").isEmpty())
                .andExpect(jsonPath("$.fullyConfirmed").value(false));
    }

    @Test
    void 사용자가_입력한_귀속월이_OCR값보다_우선() throws Exception {
        mockMvc.perform(upload("agreed", PNG).param("payPeriod", "2026-05"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payPeriod").value("2026-05"));
    }

    @Test
    void 귀속월_형식이_틀리면_400() throws Exception {
        mockMvc.perform(upload("agreed", PNG).param("payPeriod", "2026/05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 같은_귀속월을_다시_올리면_경고가_붙는다() throws Exception {
        mockMvc.perform(upload("agreed", PNG)).andExpect(status().isCreated());
        mockMvc.perform(upload("agreed", PNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0].reason").value("같은 귀속월(2026-03) 명세서가 이미 있습니다."));
    }

    @Test
    void 약관에_동의하지_않으면_403() throws Exception {
        mockMvc.perform(upload("not-agreed", PNG))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_AGREED"));
    }

    @Test
    void 이미지_PDF가_아니면_400() throws Exception {
        mockMvc.perform(upload("agreed", "not an image".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    void file_파트가_없으면_400() throws Exception {
        mockMvc.perform(multipart("/api/payslips").header("X-User-Subject", "agreed"))
                .andExpect(status().isBadRequest());
    }

    /** 데모 시나리오 전체: 업로드 → 모든 필드 확인 → 점검 */
    @Test
    void 업로드부터_점검까지_전체_흐름() throws Exception {
        String uploadBody = mockMvc.perform(upload("agreed", PNG))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer payslipId = JsonPath.read(uploadBody, "$.id");

        // 모든 필드를 OCR 값 그대로 확인(식대만 사용자가 고쳤다고 가정)
        String confirmations = payslipRepository.findById(payslipId.longValue()).orElseThrow().getFields().stream()
                .map(PayslipField::getKey)
                .map(key -> "{\"key\":\"%s\",\"value\":%s}".formatted(key.getCode(),
                        switch (key) {
                            case MEAL_ALLOWANCE -> "200000";
                            default -> String.valueOf(payslipRepository.findById(payslipId.longValue()).orElseThrow()
                                    .findField(key).orElseThrow().getValue());
                        }))
                .collect(Collectors.joining(",", "{\"fields\":[", "]}"));
        mockMvc.perform(patchJsonAs("agreed", confirmations, "/api/payslips/{id}/fields", payslipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyConfirmed").value(true));

        mockMvc.perform(post("/api/payslips/{id}/check", payslipId).header("X-User-Subject", "agreed"))
                .andExpect(status().isCreated())
                // 명절 상여가 표준 항목 밖이라 지급 합계 안내(INFO)가 나온다
                .andExpect(jsonPath("$.findings[?(@.ruleCode == 'PAYMENT_TOTAL_MISMATCH')].severity").value("INFO"))
                .andExpect(jsonPath("$.findings[?(@.ruleCode == 'NET_PAY_MISMATCH')]").isEmpty());
    }
}
