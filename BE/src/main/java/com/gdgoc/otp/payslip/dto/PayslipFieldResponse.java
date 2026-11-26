package com.gdgoc.otp.payslip.dto;

import com.gdgoc.otp.payslip.domain.FieldStatus;
import com.gdgoc.otp.payslip.domain.PayslipField;

/**
 * 명세서 필드 1칸 응답. 확인 화면에서 한 줄로 그려진다.
 *
 * <p>응답 예시:</p>
 * <pre>
 * { "key": "meal_allowance", "label": "식대", "value": 200000, "confidence": 0.62,
 *   "sourceLabel": "중식대", "status": "NEEDS_REVIEW", "userConfirmed": false }
 * </pre>
 *
 * @param key           필드 코드(snake_case). 확인 요청을 보낼 때 이 값을 그대로 쓴다.
 * @param label         화면용 한글 이름
 * @param value         금액(원). 못 읽었으면 null
 * @param confidence    OCR 신뢰도. 사람이 고친 값이면 null
 * @param sourceLabel   명세서 원문 항목명(근거 표시용)
 * @param status        OK / NEEDS_REVIEW(강조 표시) / MISSING
 * @param userConfirmed 사용자가 확인했는지
 */
public record PayslipFieldResponse(
        String key,
        String label,
        Integer value,
        Double confidence,
        String sourceLabel,
        FieldStatus status,
        boolean userConfirmed
) {
    public static PayslipFieldResponse from(PayslipField field) {
        return new PayslipFieldResponse(
                field.getKey().getCode(),
                field.getKey().getKoreanName(),
                field.getValue(),
                field.getConfidence(),
                field.getSourceLabel(),
                field.getStatus(),
                field.isUserConfirmed()
        );
    }
}
