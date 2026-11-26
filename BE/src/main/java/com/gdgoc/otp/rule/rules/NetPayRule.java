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
 * 실수령액 검증: 지급액 합계 − 공제액 합계 = 실수령액 인지 본다. 순수 산수 규칙.
 *
 * <p>세 값 중 하나라도 없으면 판단하지 않는다. 맞지 않으면 WARNING.
 * 실제로 돈을 덜 받았을 수도 있고, 단순히 잘못 읽었을 수도 있으므로 확인을 요청한다.</p>
 */
@Component
@Order(20)
public class NetPayRule implements CheckRule {

    public static final String CODE = "NET_PAY_MISMATCH";

    @Override
    public List<RuleResult> evaluate(CheckContext context) {
        Optional<Integer> payment = context.amount(PayslipFieldKey.PAYMENT_TOTAL);
        Optional<Integer> deduction = context.amount(PayslipFieldKey.DEDUCTION_TOTAL);
        Optional<Integer> netPay = context.amount(PayslipFieldKey.NET_PAY);
        if (payment.isEmpty() || deduction.isEmpty() || netPay.isEmpty()) {
            return List.of();
        }

        long expected = (long) payment.get() - deduction.get();
        if (expected == netPay.get()) {
            return List.of();
        }
        return List.of(RuleResult.of(CODE, Severity.WARNING,
                "지급액 합계(%s)에서 공제액 합계(%s)를 빼면 %s인데, 실수령액은 %s로 적혀 있습니다. 명세서 금액을 다시 확인해 주세요."
                        .formatted(Won.format(payment.get()), Won.format(deduction.get()),
                                Won.format(expected), Won.format(netPay.get()))));
    }
}
