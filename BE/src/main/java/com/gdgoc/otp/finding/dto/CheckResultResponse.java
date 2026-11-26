package com.gdgoc.otp.finding.dto;

import com.gdgoc.otp.task.dto.UserTaskResponse;
import com.gdgoc.otp.taxrule.domain.RuleStatus;

import java.util.List;

/**
 * 점검 실행 결과 응답.
 *
 * <p>응답 예시:</p>
 * <pre>
 * {
 *   "payslipId": 1,
 *   "ruleVersion": "2026-demo-v1",
 *   "ruleStatus": "DRAFT",           ← DRAFT면 화면에 "검증 전 규칙" 경고를 띄운다
 *   "findings": [ { "ruleCode": "SME_EMPLOYEE_TAX_REDUCTION", "severity": "WARNING", ... } ],
 *   "createdTasks": [ { "title": "중소기업 취업자 소득세 감면 신청서 제출", ... } ]
 * }
 * </pre>
 *
 * @param createdTasks 이번 점검으로 새로 생긴 할 일. 이미 받은 할 일은 다시 만들지 않으므로 포함되지 않는다
 */
public record CheckResultResponse(
        Long payslipId,
        String ruleVersion,
        RuleStatus ruleStatus,
        List<FindingResponse> findings,
        List<UserTaskResponse> createdTasks
) {
}
