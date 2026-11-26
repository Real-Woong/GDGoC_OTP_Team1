package com.gdgoc.otp.payslip.domain;

/**
 * 명세서 검증 경고 1건. Payslip.warnings(JSON 배열)의 원소 하나다.
 *
 * <p>예: 지급 항목 합이 지급액 합계와 다를 때
 * {@code new PayslipWarning("payment_total", "지급 항목 합계와 1,000원 차이")}</p>
 *
 * @param field  관련 필드 코드(PayslipFieldKey.code). 명세서 전체에 대한 경고면 null
 * @param reason 사람이 읽을 수 있는 경고 사유
 */
public record PayslipWarning(String field, String reason) {
}
