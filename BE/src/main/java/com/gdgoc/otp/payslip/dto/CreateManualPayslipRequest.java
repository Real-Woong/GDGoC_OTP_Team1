package com.gdgoc.otp.payslip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 직접 입력용 빈 명세서 만들기 요청. POST /api/payslips/manual
 *
 * <p>OCR이 실패했거나(명세서가 아님, 화질 낮음 등) 사용자가 처음부터 손으로 입력하고 싶을 때 쓴다.
 * 이 요청은 14개 항목이 모두 비어 있는(MISSING) 명세서를 만들고, 금액은 이후
 * PATCH /api/payslips/{id}/fields 로 채운다. 업로드 후 확인 화면과 같은 화면·API를 그대로 쓰기 위해서다.</p>
 *
 * @param payPeriod 귀속월 "YYYY-MM". 직접 입력에서는 읽어 올 명세서가 없으므로 필수다
 */
public record CreateManualPayslipRequest(
        @NotBlank(message = "귀속월은 필수입니다.")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "귀속월은 YYYY-MM 형식이어야 합니다.")
        String payPeriod
) {
}
