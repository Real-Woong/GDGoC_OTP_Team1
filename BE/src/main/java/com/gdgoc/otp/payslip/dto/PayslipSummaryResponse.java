package com.gdgoc.otp.payslip.dto;

import com.gdgoc.otp.payslip.domain.Payslip;

import java.time.Instant;

/**
 * 명세서 목록의 한 줄. 목록 화면에서는 필드까지 필요 없으므로 요약만 준다.
 * (필드를 같이 주면 명세서 수만큼 필드 조회 쿼리가 더 나간다.)
 */
public record PayslipSummaryResponse(Long id, String payPeriod, String companyName, Instant createdAt) {

    public static PayslipSummaryResponse from(Payslip payslip) {
        return new PayslipSummaryResponse(payslip.getId(), payslip.getPayPeriod(),
                payslip.getCompanyName(), payslip.getCreatedAt());
    }
}
