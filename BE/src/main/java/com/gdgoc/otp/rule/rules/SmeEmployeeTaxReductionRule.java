package com.gdgoc.otp.rule.rules;

import com.gdgoc.otp.finding.domain.Severity;
import com.gdgoc.otp.rule.engine.CheckContext;
import com.gdgoc.otp.rule.engine.CheckRule;
import com.gdgoc.otp.rule.engine.RuleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 중소기업 취업자 소득세 감면 안내. 이 서비스의 핵심 차별 기능 후보다.
 *
 * <p>필요한 규칙 수치: parameters.smeEmployeeReduction (나이·기간·감면율·한도 등 요건 묶음)
 * — 시드 값은 null(미검증)이다.</p>
 *
 * <p>현재 동작 (수치가 없으므로 "가능성 안내 + 확인 할 일"까지만 한다):</p>
 * <ul>
 *   <li>중소기업 여부를 모름(null) → INFO: 내 정보에 입력하면 점검할 수 있다고 안내.</li>
 *   <li>중소기업 아님(false) → 해당 없음, 결과 없음.</li>
 *   <li>중소기업 재직(true) → WARNING: 감면 대상일 수 있으니 회사에 신청 여부를 확인하라고 안내하고,
 *       "감면 신청서 제출" 할 일을 만든다.</li>
 * </ul>
 *
 * <p>TODO(팀): 요건 수치가 검증되면 나이 요건(출생연도·입사일 기준), 감면 기간 등을 계산해
 * "대상 아님"을 걸러내는 로직을 추가한다. 나이 계산 기준(만 나이, 병역 기간 등)은 반드시 공식 출처로 확인할 것.</p>
 */
@Component
@Order(50)
public class SmeEmployeeTaxReductionRule implements CheckRule {

    public static final String CODE = "SME_EMPLOYEE_TAX_REDUCTION";
    public static final String NEED_INPUT_CODE = "SME_EMPLOYEE_STATUS_UNKNOWN";
    /** V2 시드의 할 일 템플릿 키. */
    public static final String TASK_TEMPLATE_KEY = "sme-reduction-application";

    @Override
    public List<RuleResult> evaluate(CheckContext context) {
        Boolean isSme = context.isSmeEmployee();
        if (isSme == null) {
            return List.of(RuleResult.of(NEED_INPUT_CODE, Severity.INFO,
                    "중소기업 재직 여부를 내 정보에 입력하면 중소기업 취업자 소득세 감면 대상인지 점검해 드릴게요."));
        }
        if (!isSme) {
            return List.of();
        }
        return List.of(RuleResult.withTask(CODE, Severity.WARNING,
                "중소기업 취업자 소득세 감면 대상일 수 있습니다. 감면은 회사에 신청서를 내야 적용되므로, 신청했는지 급여 담당자에게 확인해 보세요. (나이·기간 등 세부 요건은 아직 검증 중입니다)",
                TASK_TEMPLATE_KEY));
    }
}
