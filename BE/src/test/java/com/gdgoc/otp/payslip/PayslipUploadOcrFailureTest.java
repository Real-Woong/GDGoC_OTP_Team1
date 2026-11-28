package com.gdgoc.otp.payslip;

import com.gdgoc.otp.infra.ocr.OcrClient;
import com.gdgoc.otp.infra.ocr.OcrException;
import com.gdgoc.otp.infra.ocr.OcrRequest;
import com.gdgoc.otp.infra.ocr.OcrResult;
import com.gdgoc.otp.support.ApiTestSupport;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OCR이 실패하거나 이상한 결과를 줄 때의 업로드 동작.
 *
 * <p>{@code @MockitoBean}: 스프링에 등록된 OcrClient(FakeOcrClient)를 이 테스트 동안만
 * Mockito 가짜 객체로 바꿔 끼운다. given(...).willThrow(...)로 원하는 실패 상황을 만들 수 있다.
 * 실제 OCR 연동 후에도 이 테스트는 그대로 쓸 수 있다.</p>
 */
class PayslipUploadOcrFailureTest extends ApiTestSupport {

    @MockitoBean OcrClient ocrClient;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User agreed = User.create("agreed");
        agreed.agreeToTerms("2026-11-v1", Instant.now());
        userRepository.save(agreed);
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "payslip.png", "image/png", UploadedFileValidatorTest.PNG);
    }

    @Test
    void OCR_서버가_응답하지_않으면_503과_일반_문구() throws Exception {
        given(ocrClient.extract(any())).willThrow(new OcrException("timeout after 30s"));

        mockMvc.perform(multipart("/api/payslips").file(png()).header("X-User-Subject", "agreed"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OCR_UNAVAILABLE"))
                // 내부 원인(timeout)은 응답에 나가지 않는다
                .andExpect(jsonPath("$.message").value("지금은 명세서를 읽을 수 없습니다. 잠시 후 다시 시도하거나 직접 입력해 주세요."));
    }

    @Test
    void 비밀번호_PDF면_428과_전용_코드() throws Exception {
        given(ocrClient.extract(any())).willThrow(
                new OcrException("OCR 서버 428", "PDF_PASSWORD_REQUIRED", "비밀번호가 걸린 PDF예요."));

        mockMvc.perform(multipart("/api/payslips").file(png()).header("X-User-Subject", "agreed"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PDF_PASSWORD_REQUIRED"));
    }

    @Test
    void 비밀번호가_틀리면_428과_전용_코드() throws Exception {
        given(ocrClient.extract(any())).willThrow(
                new OcrException("OCR 서버 428", "PDF_PASSWORD_INVALID", "비밀번호가 맞지 않아요."));

        mockMvc.perform(multipart("/api/payslips").file(png()).param("pdfPassword", "000000")
                        .header("X-User-Subject", "agreed"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PDF_PASSWORD_INVALID"));
    }

    @Test
    void 입력한_비밀번호와_귀속연도가_OCR로_전달된다() throws Exception {
        given(ocrClient.extract(any())).willReturn(new OcrResult(null, null, List.of(), List.of()));

        mockMvc.perform(multipart("/api/payslips").file(png()).param("payPeriod", "2026-02")
                        .param("pdfPassword", "990101").header("X-User-Subject", "agreed"))
                .andExpect(status().isCreated());

        // ArgumentCaptor: 목 객체가 어떤 인자로 호출됐는지 꺼내 보는 도구
        ArgumentCaptor<OcrRequest> captor = ArgumentCaptor.forClass(OcrRequest.class);
        then(ocrClient).should().extract(captor.capture());
        assertThat(captor.getValue().pdfPassword()).isEqualTo("990101");
        assertThat(captor.getValue().payYear()).isEqualTo(2026);
    }

    @Test
    void 파일_문제는_INVALID_FILE과_OCR서버_문장() throws Exception {
        given(ocrClient.extract(any())).willThrow(
                new OcrException("OCR 서버 413", "TOO_MANY_PAGES", "PDF는 3페이지까지 올릴 수 있어요."));

        mockMvc.perform(multipart("/api/payslips").file(png()).header("X-User-Subject", "agreed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"))
                .andExpect(jsonPath("$.message").value("PDF는 3페이지까지 올릴 수 있어요."));
    }

    /**
     * OCR 서버 코드 → BE 응답 코드·HTTP 상태 매핑 표(PayslipUploadService.toBusinessException)를 한 번에 확인한다.
     * {@code @ParameterizedTest + @CsvSource}: 같은 테스트를 표의 각 줄 값으로 여러 번 돌린다.
     */
    @ParameterizedTest(name = "{0} → {1} ({2})")
    @CsvSource({
            "NOT_A_PAYSLIP,     NOT_A_PAYSLIP,     422",
            "LOW_QUALITY_IMAGE, LOW_QUALITY_IMAGE, 422",
            "EXTRACTION_FAILED, OCR_FAILED,        422",
            "CORRUPTED_FILE,    INVALID_FILE,      400",
            "UNAUTHORIZED,      OCR_UNAVAILABLE,   503",
            "SOMETHING_NEW,     OCR_UNAVAILABLE,   503",
    })
    void OCR_에러코드_매핑(String ocrCode, String expectedCode, int expectedStatus) throws Exception {
        given(ocrClient.extract(any())).willThrow(new OcrException("OCR 서버 오류", ocrCode, null));

        mockMvc.perform(multipart("/api/payslips").file(png()).header("X-User-Subject", "agreed"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    @Test
    void OCR이_귀속월을_못읽고_입력도_없으면_400() throws Exception {
        given(ocrClient.extract(any())).willReturn(new OcrResult(null, null, List.of(), List.of()));

        mockMvc.perform(multipart("/api/payslips").file(png()).header("X-User-Subject", "agreed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAY_PERIOD_REQUIRED"));
    }

    @Test
    void OCR이_귀속월을_못읽어도_입력이_있으면_저장된다() throws Exception {
        given(ocrClient.extract(any())).willReturn(new OcrResult(null, null, List.of(), List.of()));

        mockMvc.perform(multipart("/api/payslips").file(png()).param("payPeriod", "2026-02")
                        .header("X-User-Subject", "agreed"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payPeriod").value("2026-02"));
    }
}
