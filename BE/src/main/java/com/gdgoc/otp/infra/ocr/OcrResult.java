package com.gdgoc.otp.infra.ocr;

import java.util.List;

/**
 * OCR 추출 결과. OCR 파트 명세의 출력 형식을 Java로 옮긴 것이다.
 *
 * <p>이 클래스는 "외부에서 들어온 날것의 결과"다. 필드 코드가 표준인지, 금액이 맞는지는
 * 서비스(PayslipUploadService)가 검사한 뒤 엔티티로 바꾼다. 외부 형식과 내부 엔티티를 분리해 두면
 * OCR 제공자가 바뀌어도 엔티티는 그대로 둘 수 있다.</p>
 *
 * @param payPeriod     명세서에서 읽은 귀속월 "YYYY-MM". 못 읽었으면 null
 * @param companyName   회사명. 못 읽었으면 null
 * @param fields        추출한 필드들
 * @param unmappedItems 표준 필드로 못 맞춘 항목들(원문 이름과 금액)
 */
public record OcrResult(
        String payPeriod,
        String companyName,
        List<Field> fields,
        List<Unmapped> unmappedItems
) {
    public OcrResult {
        fields = fields == null ? List.of() : List.copyOf(fields);
        unmappedItems = unmappedItems == null ? List.of() : List.copyOf(unmappedItems);
    }

    /**
     * @param code        표준 필드 코드. 예: "base_salary"
     * @param value       금액(원). 못 읽었으면 null
     * @param confidence  신뢰도 0.0~1.0. 모르면 null
     * @param sourceLabel 명세서 원문 항목명. 예: "중식대"
     */
    public record Field(String code, Integer value, Double confidence, String sourceLabel) {
    }

    /**
     * @param label  원문 항목명
     * @param amount 금액(원). 못 읽었으면 null
     */
    public record Unmapped(String label, Integer amount) {
    }
}
