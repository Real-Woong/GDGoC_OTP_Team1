package com.gdgoc.otp.payslip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * 명세서 필드 확인(및 수정) 요청. 확인 화면의 "확인 완료" 버튼 한 번에 여러 필드를 보낸다.
 *
 * <p>요청 예시: 기본급은 그대로 확인, 식대는 200,000원으로 고쳐서 확인, 상여는 "없음"으로 확인</p>
 * <pre>
 * PATCH /api/payslips/1/fields
 * {
 *   "fields": [
 *     { "key": "base_salary",    "value": 2500000 },
 *     { "key": "meal_allowance", "value": 200000 },
 *     { "key": "bonus",          "value": null }
 *   ]
 * }
 * </pre>
 *
 * <p>{@code @Valid}를 리스트에 붙여야 리스트 안 각 원소의 검증(@NotBlank 등)도 실행된다.</p>
 */
public record ConfirmFieldsRequest(
        @NotEmpty(message = "확인할 항목이 없습니다.")
        List<@Valid FieldConfirmation> fields
) {
    /**
     * @param key   필드 코드. 예: "base_salary"
     * @param value 사용자가 확정한 금액(원). 해당 항목이 없으면 null
     */
    public record FieldConfirmation(
            @NotBlank(message = "필드 코드는 필수입니다.")
            String key,

            @PositiveOrZero(message = "금액은 0 이상이어야 합니다.")
            Integer value
    ) {
    }
}
