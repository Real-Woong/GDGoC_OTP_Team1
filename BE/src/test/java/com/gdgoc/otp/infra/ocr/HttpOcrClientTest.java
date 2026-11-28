package com.gdgoc.otp.infra.ocr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * HttpOcrClient 단위 테스트. 실제 OCR 서버 없이 돈다.
 *
 * <p>{@link MockRestServiceServer}: RestClient가 보내는 요청을 가로채서
 * "이 주소로 이런 요청이 오면 이 응답을 줘라"를 미리 정해 두는 가짜 서버.
 * 응답 JSON은 OCR/docs/api/ocr-api.md의 예시를 바탕으로 만들었다.
 * OCR 서버 응답 형식이 바뀌면 이 테스트의 JSON도 함께 바꾼다.</p>
 */
class HttpOcrClientTest {

    private static final String BASE_URL = "http://ocr.test";

    MockRestServiceServer server;
    HttpOcrClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL).defaultHeader("X-API-Key", "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpOcrClient(builder.build());
    }

    private OcrRequest pdf(String password, Integer payYear) {
        return new OcrRequest("%PDF-1.4".getBytes(), "application/pdf", password, payYear);
    }

    /** OCR 서버 성공 응답 예시(필드 14개 중 일부만. 없는 필드는 Assembler가 MISSING으로 채운다). */
    private static final String SUCCESS_JSON = """
            {
              "request_id": "3dc3bd83718c4139bd0c2aed35a4a511",
              "pay_period": "2026-03",
              "join_date": {"value": "2026-03-18", "source_text": "2026-03-18", "confidence": 0.99, "status": "needs_review"},
              "hints": [{"code": "FIRST_MONTH", "message": "입사한 달의 급여예요."}],
              "fields": {
                "base_salary": {"value": 1354830, "category": "payment", "source_label": "기본급(일할)", "confidence": 0.99,
                                "match_method": "normalized", "status": "ok", "sources": []},
                "income_tax": {"value": 0, "category": "deduction", "source_label": "소득세", "confidence": 0.45,
                               "match_method": "exact", "status": "needs_review", "sources": []},
                "bonus": {"value": null, "category": "payment", "source_label": null, "confidence": null,
                          "match_method": null, "status": "missing", "sources": []}
              },
              "unmapped_items": [{"source_label": "직책수당", "value": 100000, "confidence": 0.99,
                                  "reason": "not_in_dictionary", "qualifiers": [], "category": "payment"}],
              "unmatched_amounts": [],
              "validation": {"summary": "warning", "rate_year": 2026, "checks": [
                {"code": "TOTAL_OK", "level": "pass", "field": "payment_total", "expected": null, "actual": null, "message": null},
                {"code": "FIELD_NEEDS_REVIEW", "level": "warning", "field": "income_tax", "expected": null, "actual": 0,
                 "message": "'소득세' 값을 확인해 주세요."}
              ]}
            }
            """;

    @Test
    void 성공_응답을_내부_형식으로_바꾼다() {
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-key"))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        OcrResult result = client.extract(pdf(null, null));

        assertThat(result.payPeriod()).isEqualTo("2026-03");
        assertThat(result.companyName()).isNull();
        assertThat(result.fields()).hasSize(3);
        assertThat(result.fields()).contains(
                new OcrResult.Field("base_salary", 1354830, 0.99, "기본급(일할)", false),
                new OcrResult.Field("income_tax", 0, 0.45, "소득세", true),
                new OcrResult.Field("bonus", null, null, null, false));
        assertThat(result.unmappedItems()).containsExactly(new OcrResult.Unmapped("직책수당", 100000));
        // pass는 버리고 warning만 남긴다
        assertThat(result.warnings()).containsExactly(new OcrResult.Warning("income_tax", "'소득세' 값을 확인해 주세요."));
        // validation이 있으므로 BE는 합계 검증을 다시 하지 않는다
        assertThat(result.validated()).isTrue();
        server.verify();
    }

    @Test
    void 파일_비밀번호_연도를_multipart로_보낸다() {
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andExpect(content().multipartDataContains(Map.of(
                        "pdf_password", "990101",
                        "pay_year", "2026")))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        client.extract(pdf("990101", 2026));
        server.verify();
    }

    @Test
    void 비밀번호_PDF_428이면_코드와_문장을_담아_던진다() {
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_REQUIRED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"request_id": "r1", "error": {"code": "PDF_PASSWORD_REQUIRED",
                         "message": "비밀번호가 걸린 PDF예요. 비밀번호를 입력해 주세요."}}
                        """));

        assertThatThrownBy(() -> client.extract(pdf(null, null)))
                .isInstanceOfSatisfying(OcrException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("PDF_PASSWORD_REQUIRED");
                    assertThat(e.getUserMessage()).isEqualTo("비밀번호가 걸린 PDF예요. 비밀번호를 입력해 주세요.");
                });
    }

    @Test
    void 명세서가_아니면_422_문장을_사용자용으로_담는다() {
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"request_id": "r2", "error": {"code": "NOT_A_PAYSLIP", "message": "급여명세서로 보이지 않아요."}}
                        """));

        assertThatThrownBy(() -> client.extract(pdf(null, null)))
                .isInstanceOfSatisfying(OcrException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("NOT_A_PAYSLIP");
                    assertThat(e.getUserMessage()).isEqualTo("급여명세서로 보이지 않아요.");
                });
    }

    @Test
    void API키_불일치_401은_사용자용_문장을_주지_않는다() {
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"request_id": "r3", "error": {"code": "UNAUTHORIZED", "message": "인증에 실패했어요."}}
                        """));

        // 서버 설정 문제라 사용자에게 "인증 실패"를 보여 주면 혼란스럽다 → 일반 문구로 처리되도록 null
        assertThatThrownBy(() -> client.extract(pdf(null, null)))
                .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.getUserMessage()).isNull());
    }

    @Test
    void OCR_서버_오류_502도_일반_실패() {
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body("""
                        {"request_id": "r4", "error": {"code": "EXTRACTION_FAILED", "message": "명세서를 처리하지 못했어요."}}
                        """));

        assertThatThrownBy(() -> client.extract(pdf(null, null)))
                .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.getUserMessage()).isNull());
    }

    @Test
    void 에러_본문_형식이_달라도_OcrException() {
        // FastAPI 기본 422(요청 형식 오류)는 {"detail": [...]} 모양이다
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\": [{\"msg\": \"field required\"}]}"));

        assertThatThrownBy(() -> client.extract(pdf(null, null)))
                .isInstanceOfSatisfying(OcrException.class, e -> {
                    assertThat(e.getCode()).isNull();
                    assertThat(e.getUserMessage()).isNull();
                });
    }

    @Test
    void 연결_실패나_타임아웃은_OcrException으로_바꾼다() {
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> client.extract(pdf(null, null)))
                .isInstanceOf(OcrException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    void 성공_응답이_JSON이_아니면_OcrException() {
        server.expect(requestTo(BASE_URL + "/payslip/extract"))
                .andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.extract(pdf(null, null))).isInstanceOf(OcrException.class);
    }

    @Test
    void 비밀번호는_toString에_나오지_않는다() {
        assertThat(pdf("990101", 2026).toString()).doesNotContain("990101");
    }
}
