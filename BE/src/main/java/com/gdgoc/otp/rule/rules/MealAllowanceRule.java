package com.gdgoc.otp.rule.rules;

import com.gdgoc.otp.finding.domain.Severity;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.rule.engine.CheckContext;
import com.gdgoc.otp.rule.engine.CheckRule;
import com.gdgoc.otp.rule.engine.RuleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 식대 비과세 한도 안내.
 *
 * <p>필요한 규칙 수치: parameters.mealAllowanceNonTaxableLimit (월 비과세 한도, 원)
 * — 현재 시드에는 이 키가 없다(미검증).</p>
 *
 * <ul>
 *   <li>식대가 0원이거나 없으면 판단하지 않는다.</li>
 *   <li>한도 수치가 없으면: INFO로 "한도는 확인 중"이라고만 안내한다. 수치를 추측하지 않는다.</li>
 *   <li>한도 수치가 있고 식대가 한도를 넘으면: INFO로 "넘는 부분은 과세될 수 있음"을 안내한다.
 *       (비과세 요건은 금액 외에도 있으므로 단정하지 않는다.)</li>
 * </ul>
 */
@Component
@Order(40)
public class MealAllowanceRule implements CheckRule {

    public static final String CODE = "MEAL_ALLOWANCE_NON_TAXABLE";
    public static final String LIMIT_PARAMETER = "mealAllowanceNonTaxableLimit";

    @Override
    public List<RuleResult> evaluate(CheckContext context) {
        Optional<Integer> meal = context.amount(PayslipFieldKey.MEAL_ALLOWANCE).filter(amount -> amount > 0);
        if (meal.isEmpty()) {
            return List.of();
        }

        Optional<Number> limit = context.numberParameter(LIMIT_PARAMETER);
        if (limit.isEmpty()) {
            return List.of(RuleResult.of(CODE, Severity.INFO,
                    "식대 %s이 지급되었습니다. 식대는 일정 한도까지 비과세될 수 있지만, 한도 기준은 아직 검증 중입니다. 회사 급여 담당자에게 비과세 처리 여부를 확인해 보세요."
                            .formatted(Won.format(meal.get()))));
        }

        long over = meal.get() - limit.get().longValue();
        if (over <= 0) {
            return List.of();
        }
        return List.of(RuleResult.of(CODE, Severity.INFO,
                "식대 %s 중 비과세 한도(%s)를 넘는 %s은 과세될 수 있습니다."
                        .formatted(Won.format(meal.get()), Won.format(limit.get().longValue()), Won.format(over))));
    }
}
