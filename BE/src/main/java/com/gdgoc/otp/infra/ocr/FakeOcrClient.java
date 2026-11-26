package com.gdgoc.otp.infra.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ⚠️ 가짜 OCR. 어떤 파일을 올려도 항상 같은 샘플 결과를 돌려준다. 개발·데모 전용.
 *
 * <p>목적: 실제 OCR 제공자가 정해지기 전에도 FE·BE가
 * "업로드 → 확인 → 점검 → 할 일" 전체 흐름을 만들고 시연할 수 있게 하는 것.</p>
 *
 * <p>샘플은 확인 화면의 여러 상태를 보여주도록 일부러 섞었다(금액은 가상의 예시다):</p>
 * <ul>
 *   <li>대부분 필드: 신뢰도 높음 → OK</li>
 *   <li>식대: 신뢰도 0.62 → NEEDS_REVIEW (화면에서 강조)</li>
 *   <li>상여: 못 읽음 → MISSING</li>
 *   <li>"명절 상여": 표준 필드가 아님 → unmappedItems</li>
 * </ul>
 * 숫자는 지급 합계 − 공제 합계 = 실수령액이 맞도록 맞춰 두었다.
 *
 * <p>{@code @ConditionalOnProperty}: application.yml의 otp.ocr.provider가 "fake"이거나
 * 값이 없을 때만 이 빈이 등록된다. 실제 구현을 추가하면 provider 값을 바꿔 이 빈을 끈다.</p>
 */
@Component
@ConditionalOnProperty(name = "otp.ocr.provider", havingValue = "fake", matchIfMissing = true)
public class FakeOcrClient implements OcrClient {

    private static final Logger log = LoggerFactory.getLogger(FakeOcrClient.class);

    @Override
    public OcrResult extract(byte[] content, String contentType) {
        // 파일 내용은 로그에 남기지 않는다(개인정보). 크기와 형식만 남긴다.
        log.warn("FakeOcrClient 사용 중: 실제 OCR이 아니라 고정 샘플을 반환합니다. ({} bytes, {})",
                content.length, contentType);

        return new OcrResult(
                "2026-03",
                "샘플주식회사",
                List.of(
                        new OcrResult.Field("base_salary", 2_400_000, 0.98, "기본급"),
                        new OcrResult.Field("meal_allowance", 200_000, 0.62, "중식대"),
                        new OcrResult.Field("overtime_pay", 150_000, 0.91, "연장근로수당"),
                        new OcrResult.Field("bonus", null, null, null),
                        new OcrResult.Field("payment_total", 2_850_000, 0.97, "지급액계"),
                        new OcrResult.Field("national_pension", 112_500, 0.95, "국민연금"),
                        new OcrResult.Field("health_insurance", 88_600, 0.95, "건강보험"),
                        new OcrResult.Field("long_term_care", 11_470, 0.93, "장기요양"),
                        new OcrResult.Field("employment_insurance", 22_500, 0.95, "고용보험"),
                        new OcrResult.Field("income_tax", 65_000, 0.94, "소득세"),
                        new OcrResult.Field("local_income_tax", 6_500, 0.94, "지방소득세"),
                        new OcrResult.Field("deduction_total", 306_570, 0.96, "공제액계"),
                        new OcrResult.Field("net_pay", 2_543_430, 0.97, "차인지급액")
                ),
                List.of(new OcrResult.Unmapped("명절 상여", 100_000))
        );
    }
}
