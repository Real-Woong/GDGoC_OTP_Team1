package com.gdgoc.otp.rule.engine;

import com.gdgoc.otp.finding.domain.Severity;

/**
 * 규칙 하나가 낸 판단 결과 1건. 서비스가 이것을 CheckFinding으로 저장한다.
 *
 * @param ruleCode        결과 종류 코드. 예: "NET_PAY_MISMATCH"
 * @param severity        중요도
 * @param message         사용자에게 보여줄 안내 문구. "~일 수 있습니다", "확인하세요"처럼 단정하지 않는다
 * @param taskTemplateKey 이 결과로 할 일을 만들어야 하면 TaskTemplate.key, 아니면 null
 */
public record RuleResult(String ruleCode, Severity severity, String message, String taskTemplateKey) {

    /** 할 일 없이 안내만 하는 결과. */
    public static RuleResult of(String ruleCode, Severity severity, String message) {
        return new RuleResult(ruleCode, severity, message, null);
    }

    /** 할 일도 함께 만드는 결과. */
    public static RuleResult withTask(String ruleCode, Severity severity, String message, String taskTemplateKey) {
        return new RuleResult(ruleCode, severity, message, taskTemplateKey);
    }
}
