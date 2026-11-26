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
 * 지방소득세 비율 검증: 지방소득세가 소득세에 비해 예상 비율과 크게 다른지 본다.
 *
 * <p>필요한 규칙 수치: parameters.validation.localIncomeTaxRelativeRate (소득세 대비 비율)
 * — 시드 값은 null(미검증)이다. 수치가 없으면 이 규칙은 아무것도 하지 않는다.
 * 팀이 공식 출처로 비율을 확인해 새 규칙 버전(V3 마이그레이션)으로 넣으면 그때부터 동작한다.</p>
 *
 * <p>원 단위 절사 때문에 몇 원 차이는 정상이므로 허용 오차를 둔다.</p>
 */
@Component
@Order(30)
public class LocalIncomeTaxRule implements CheckRule {

    public static final String CODE = "LOCAL_INCOME_TAX_RATIO";

    /** 절사 오차 허용 범위(원). */
    static final long TOLERANCE_WON = 10;

    @Override
    public List<RuleResult> evaluate(CheckContext context) {
        Optional<Number> rate = context.objectParameter("validation")
                .map(validation -> validation.get("localIncomeTaxRelativeRate"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast);
        Optional<Integer> incomeTax = context.amount(PayslipFieldKey.INCOME_TAX);
        Optional<Integer> localTax = context.amount(PayslipFieldKey.LOCAL_INCOME_TAX);
        if (rate.isEmpty() || incomeTax.isEmpty() || localTax.isEmpty()) {
            return List.of();
        }

        long expected = Math.round(incomeTax.get() * rate.get().doubleValue());
        if (Math.abs(expected - localTax.get()) <= TOLERANCE_WON) {
            return List.of();
        }
        return List.of(RuleResult.of(CODE, Severity.INFO,
                "지방소득세(%s)가 소득세 기준 예상값(약 %s)과 다릅니다. 금액을 잘못 읽었는지 확인해 주세요."
                        .formatted(Won.format(localTax.get()), Won.format(expected))));
    }
}
