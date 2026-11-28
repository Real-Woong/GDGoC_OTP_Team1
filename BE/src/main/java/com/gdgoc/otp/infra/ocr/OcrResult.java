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
 * @param validated     OCR이 합계·실수령액 검증을 직접 했는지. true면 BE는 같은 검증을 다시 하지 않는다
 *                      (같은 문제가 경고로 두 번 나오지 않도록). 가짜 OCR처럼 검증하지 않는 구현은 false
 */
public record OcrResult(
        String payPeriod,
        String companyName,
        List<Field> fields,
        List<Unmapped> unmappedItems,
        List<Warning> warnings,
        boolean validated
) {
    public OcrResult {
        fields = fields == null ? List.of() : List.copyOf(fields);
        unmappedItems = unmappedItems == null ? List.of() : List.copyOf(unmappedItems);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** 경고·자체 검증 없이 만들 때(가짜 OCR, 직접 입력, 테스트). */
    public OcrResult(String payPeriod, String companyName, List<Field> fields, List<Unmapped> unmappedItems) {
        this(payPeriod, companyName, fields, unmappedItems, List.of(), false);
    }

    /** 경고는 있지만 합계 검증은 하지 않은 결과. */
    public OcrResult(String payPeriod, String companyName, List<Field> fields, List<Unmapped> unmappedItems,
                     List<Warning> warnings) {
        this(payPeriod, companyName, fields, unmappedItems, warnings, false);
    }

    /**
     * @param code        표준 필드 코드. 예: "base_salary"
     * @param value       금액(원). 못 읽었으면 null
     * @param confidence  신뢰도 0.0~1.0. 모르면 null
     * @param sourceLabel 명세서 원문 항목명. 예: "중식대"
     * @param needsReview OCR의 "사용자 확인 필요" 판정.
     *                    <ul>
     *                      <li>true/false: OCR이 직접 판정했다(팀 OCR 서버). BE는 이 판정을 그대로 따른다.
     *                          OCR은 신뢰도뿐 아니라 요율·합계 검증까지 보고 판정하므로 BE의 단순 기준보다 정확하다.</li>
     *                      <li>null: OCR이 판정을 주지 않았다(가짜 OCR 등). BE가 신뢰도 0.8 기준으로 판정한다.</li>
     *                    </ul>
     */
    public record Field(String code, Integer value, Double confidence, String sourceLabel, Boolean needsReview) {

        /** OCR 판정 없이 만들 때(가짜 OCR, 테스트). BE가 신뢰도로 판정한다. */
        public Field(String code, Integer value, Double confidence, String sourceLabel) {
            this(code, value, confidence, sourceLabel, null);
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
