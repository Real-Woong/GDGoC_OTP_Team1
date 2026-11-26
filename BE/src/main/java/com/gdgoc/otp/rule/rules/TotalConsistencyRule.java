package com.gdgoc.otp.rule.rules;

import com.gdgoc.otp.finding.domain.Severity;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey.Category;
import com.gdgoc.otp.rule.engine.CheckContext;
import com.gdgoc.otp.rule.engine.CheckRule;
import com.gdgoc.otp.rule.engine.RuleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 합계 검증: 항목들을 더한 값이 명세서의 합계와 맞는지 본다.
 * 세법 수치가 필요 없는 순수 산수라서 규칙 버전과 무관하게 항상 동작한다.
 *
 * <ul>
 *   <li>지급 항목(기본급·식대·…·상여) 합 vs 지급액 합계</li>
 *   <li>공제 항목(국민연금·…·지방소득세) 합 vs 공제액 합계</li>
 * </ul>
 *
 * <p>판단:</p>
 * <ul>
 *   <li>항목 합 &gt; 합계 → WARNING. 어딘가 잘못 읽었거나 잘못 입력했을 가능성이 크다.</li>
 *   <li>항목 합 &lt; 합계 → INFO. 14개 표준 필드에 없는 항목(회사 고유 수당 등)이 있으면
 *       정상적으로도 생기는 차이라 경고하지 않고 안내만 한다.</li>
 *   <li>합계 칸이 없거나, 더할 항목이 하나도 없으면 판단하지 않는다.</li>
 * </ul>
 */
@Component
@Order(10)
public class TotalConsistencyRule implements CheckRule {

    public static final String PAYMENT_CODE = "PAYMENT_TOTAL_MISMATCH";
    public static final String DEDUCTION_CODE = "DEDUCTION_TOTAL_MISMATCH";

    @Override
    public List<RuleResult> evaluate(CheckContext context) {
        List<RuleResult> results = new ArrayList<>();
        compare(context, Category.PAYMENT, PayslipFieldKey.PAYMENT_TOTAL, PAYMENT_CODE, "지급").ifPresent(results::add);
        compare(context, Category.DEDUCTION, PayslipFieldKey.DEDUCTION_TOTAL, DEDUCTION_CODE, "공제").ifPresent(results::add);
        return results;
    }

    private Optional<RuleResult> compare(CheckContext context, Category itemCategory, PayslipFieldKey totalKey,
                                         String ruleCode, String label) {
        Optional<Integer> total = context.amount(totalKey);
        // 값이 있는 항목만 더한다. (long으로 더해 int 범위를 넘는 경우를 막는다)
        List<Integer> items = Arrays.stream(PayslipFieldKey.values())
                .filter(key -> key.getCategory() == itemCategory)
                .map(context::amount)
                .flatMap(Optional::stream)
                .toList();
        if (total.isEmpty() || items.isEmpty()) {
            return Optional.empty();
        }

        long sum = items.stream().mapToLong(Integer::longValue).sum();
        long diff = sum - total.get();
        if (diff > 0) {
            return Optional.of(RuleResult.of(ruleCode, Severity.WARNING,
                    "%s 항목을 더한 값(%s)이 %s액 합계(%s)보다 %s 많습니다. 금액을 잘못 읽었거나 입력했는지 확인해 주세요."
                            .formatted(label, Won.format(sum), label, Won.format(total.get()), Won.format(diff))));
        }
        if (diff < 0) {
            return Optional.of(RuleResult.of(ruleCode, Severity.INFO,
                    "%s 항목을 더한 값이 %s액 합계보다 %s 적습니다. 표준 항목 외 다른 %s 항목이 있으면 정상입니다."
                            .formatted(label, label, Won.format(-diff), label)));
        }
        return Optional.empty();
    }
}
