package com.gdgoc.otp.finding.dto;

import com.gdgoc.otp.finding.domain.CheckFinding;
import com.gdgoc.otp.finding.domain.Severity;

import java.time.Instant;

/**
 * 점검 결과 1건 응답.
 *
 * @param isEstimate 항상 true. 화면에 "예상치·점검 안내" 표시를 붙인다
 */
public record FindingResponse(
        Long id,
        String ruleCode,
        Severity severity,
        String message,
        boolean isEstimate,
        String ruleVersion,
        Instant createdAt
) {
    public static FindingResponse from(CheckFinding finding) {
        return new FindingResponse(
                finding.getId(),
                finding.getRuleCode(),
                finding.getSeverity(),
                finding.getMessage(),
                finding.isEstimate(),
                finding.getTaxRuleVersion().getVersion(),
                finding.getCreatedAt()
        );
    }
}
