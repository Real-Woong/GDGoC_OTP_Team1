package com.gdgoc.otp.infra.ocr;

import java.util.List;

/**
 * OCR 추출 결과(BE 내부 형식).
 *
 * <p>이 클래스는 "외부에서 들어온 날것의 결과"다. 필드 코드가 표준인지, 금액이 맞는지는
 * 서비스(PayslipAssembler)가 검사한 뒤 엔티티로 바꾼다. 외부 형식과 내부 엔티티를 분리해 두면
 * OCR 제공자가 바뀌어도 엔티티는 그대로 둘 수 있다.
 * OCR 서버의 JSON 모양(snake_case, fields가 객체)은 {@link HttpOcrClient}가 이 형식으로 바꿔 준다.</p>
 *
 * @param payPeriod     명세서에서 읽은 귀속월 "YYYY-MM". 못 읽었으면 null
 * @param companyName   회사명. 못 읽었으면 null (지금 OCR 서버는 회사명을 주지 않는다)
 * @param fields        추출한 필드들
 * @param unmappedItems 표준 필드로 못 맞춘 항목들(원문 이름과 금액)
 * @param warnings      OCR이 직접 남긴 경고(합계 불일치, 요율 이상 등). 명세서 확인 화면에 그대로 보여 준다
 */
public record OcrResult(
        String payPeriod,
        String companyName,
        List<Field> fields,
        List<Unmapped> unmappedItems,
        List<Warning> warnings
) {
    public OcrResult {
        fields = fields == null ? List.of() : List.copyOf(fields);
        unmappedItems = unmappedItems == null ? List.of() : List.copyOf(unmappedItems);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** 경고 없이 만들 때(가짜 OCR, 테스트). */
    public OcrResult(String payPeriod, String companyName, List<Field> fields, List<Unmapped> unmappedItems) {
        this(payPeriod, companyName, fields, unmappedItems, List.of());
    }

    /**
     * @param code        표준 필드 코드. 예: "base_salary"
     * @param value       금액(원). 못 읽었으면 null
     * @param confidence  신뢰도 0.0~1.0. 모르면 null
     * @param sourceLabel 명세서 원문 항목명. 예: "중식대"
     * @param needsReview OCR이 "사용자 확인 필요"라고 표시했는지.
     *                    신뢰도가 높아도 OCR 자체 검증(예: 요율과 안 맞음)에 걸리면 true가 될 수 있다.
     *                    BE는 신뢰도 기준(0.8 미만)과 이 값 중 하나라도 해당하면 NEEDS_REVIEW로 둔다.
     */
    public record Field(String code, Integer value, Double confidence, String sourceLabel, boolean needsReview) {

        /** OCR의 확인 필요 표시 없이 만들 때(가짜 OCR, 테스트). */
        public Field(String code, Integer value, Double confidence, String sourceLabel) {
            this(code, value, confidence, sourceLabel, false);
        }
    }

    /**
     * @param label  원문 항목명
     * @param amount 금액(원). 못 읽었으면 null. 정산·환급은 음수일 수 있다
     */
    public record Unmapped(String label, Integer amount) {
    }

    /**
     * @param field   관련 필드 코드. 특정 필드와 무관하면 null
     * @param message 사용자에게 보여 줄 문장
     */
    public record Warning(String field, String message) {
    }
}
