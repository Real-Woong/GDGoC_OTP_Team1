package com.gdgoc.otp.rule.rules;

import com.gdgoc.otp.finding.domain.Severity;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.rule.engine.CheckContext;
import com.gdgoc.otp.rule.engine.RuleResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.gdgoc.otp.payslip.domain.PayslipFieldKey.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 점검 규칙 단위 테스트. 스프링·DB 없이 규칙 객체만 만들어 바로 실행한다(빠름).
 *
 * <p>주의: 아래 테스트의 비율·한도 숫자는 "규칙 로직이 숫자를 받으면 이렇게 동작한다"를 보는 임의 값이다.
 * 실제 세법 수치가 아니다.</p>
 */
class RulesTest {

    /** 테스트용 점검 재료를 쉽게 만드는 빌더. */
    static class Ctx {
        final Map<PayslipFieldKey, Integer> fields = new EnumMap<>(PayslipFieldKey.class);
        final Map<String, Object> params = new HashMap<>();
        Boolean isSme;

        Ctx field(PayslipFieldKey key, Integer value) { fields.put(key, value); return this; }
        Ctx param(String name, Object value) { params.put(name, value); return this; }
        Ctx sme(Boolean value) { isSme = value; return this; }

        CheckContext build() {
            return new CheckContext("2026-03", 2001, LocalDate.of(2026, 3, 2), isSme, fields, params,
                    LocalDate.of(2026, 4, 10));
        }
    }

    @Nested
    class 합계검증 {
        TotalConsistencyRule rule = new TotalConsistencyRule();

        @Test
        void 합계가_맞으면_결과없음() {
            CheckContext ctx = new Ctx().field(BASE_SALARY, 2_000_000).field(MEAL_ALLOWANCE, 200_000)
                    .field(PAYMENT_TOTAL, 2_200_000).build();
            assertThat(rule.evaluate(ctx)).isEmpty();
        }

        @Test
        void 항목합이_합계보다_크면_WARNING() {
            CheckContext ctx = new Ctx().field(BASE_SALARY, 2_000_000).field(MEAL_ALLOWANCE, 200_000)
                    .field(PAYMENT_TOTAL, 2_100_000).build();
            List<RuleResult> results = rule.evaluate(ctx);
            assertThat(results).singleElement().satisfies(r -> {
                assertThat(r.ruleCode()).isEqualTo(TotalConsistencyRule.PAYMENT_CODE);
                assertThat(r.severity()).isEqualTo(Severity.WARNING);
                assertThat(r.message()).contains("100,000원 많습니다");
            });
        }

        @Test
        void 항목합이_합계보다_작으면_INFO_표준외_항목_가능성() {
            CheckContext ctx = new Ctx().field(NATIONAL_PENSION, 100_000).field(DEDUCTION_TOTAL, 150_000).build();
            assertThat(rule.evaluate(ctx)).singleElement().satisfies(r -> {
                assertThat(r.ruleCode()).isEqualTo(TotalConsistencyRule.DEDUCTION_CODE);
                assertThat(r.severity()).isEqualTo(Severity.INFO);
            });
        }

        @Test
        void 합계칸이_없으면_판단하지_않는다() {
            CheckContext ctx = new Ctx().field(BASE_SALARY, 2_000_000).build();
            assertThat(rule.evaluate(ctx)).isEmpty();
        }
    }

    @Nested
    class 실수령액검증 {
        NetPayRule rule = new NetPayRule();

        @Test
        void 지급_빼기_공제가_실수령액이면_결과없음() {
            CheckContext ctx = new Ctx().field(PAYMENT_TOTAL, 2_500_000).field(DEDUCTION_TOTAL, 300_000)
                    .field(NET_PAY, 2_200_000).build();
            assertThat(rule.evaluate(ctx)).isEmpty();
        }

        @Test
        void 다르면_WARNING() {
            CheckContext ctx = new Ctx().field(PAYMENT_TOTAL, 2_500_000).field(DEDUCTION_TOTAL, 300_000)
                    .field(NET_PAY, 2_100_000).build();
            assertThat(rule.evaluate(ctx)).singleElement()
                    .satisfies(r -> assertThat(r.severity()).isEqualTo(Severity.WARNING));
        }

        @Test
        void 값_하나라도_없으면_판단하지_않는다() {
            CheckContext ctx = new Ctx().field(PAYMENT_TOTAL, 2_500_000).field(NET_PAY, 2_100_000).build();
            assertThat(rule.evaluate(ctx)).isEmpty();
        }
    }

    @Nested
    class 지방소득세 {
        LocalIncomeTaxRule rule = new LocalIncomeTaxRule();

        @Test
        void 비율_수치가_없으면_아무것도_하지_않는다() {
            CheckContext ctx = new Ctx().field(INCOME_TAX, 50_000).field(LOCAL_INCOME_TAX, 1).build();
            assertThat(rule.evaluate(ctx)).isEmpty();
        }

        @Test
        void 비율이_있으면_오차를_넘을_때만_안내한다() {
            Map<String, Object> validation = Map.of("localIncomeTaxRelativeRate", 0.5); // 임의 값
            assertThat(rule.evaluate(new Ctx().param("validation", validation)
                    .field(INCOME_TAX, 10_000).field(LOCAL_INCOME_TAX, 5_005).build())).isEmpty();
            assertThat(rule.evaluate(new Ctx().param("validation", validation)
                    .field(INCOME_TAX, 10_000).field(LOCAL_INCOME_TAX, 7_000).build())).hasSize(1);
        }
    }

    @Nested
    class 식대 {
        MealAllowanceRule rule = new MealAllowanceRule();

        @Test
        void 한도_수치가_없으면_검증중_안내만() {
            CheckContext ctx = new Ctx().field(MEAL_ALLOWANCE, 200_000).build();
            assertThat(rule.evaluate(ctx)).singleElement().satisfies(r -> {
                assertThat(r.severity()).isEqualTo(Severity.INFO);
                assertThat(r.message()).contains("검증 중");
            });
        }

        @Test
        void 한도를_넘으면_초과분을_안내한다() {
            CheckContext ctx = new Ctx().param(MealAllowanceRule.LIMIT_PARAMETER, 100_000) // 임의 값
                    .field(MEAL_ALLOWANCE, 150_000).build();
            assertThat(rule.evaluate(ctx)).singleElement()
                    .satisfies(r -> assertThat(r.message()).contains("50,000원"));
        }

        @Test
        void 식대가_없거나_0원이면_판단하지_않는다() {
            assertThat(rule.evaluate(new Ctx().build())).isEmpty();
            assertThat(rule.evaluate(new Ctx().field(MEAL_ALLOWANCE, 0).build())).isEmpty();
        }
    }

    @Nested
    class 중소기업감면 {
        SmeEmployeeTaxReductionRule rule = new SmeEmployeeTaxReductionRule();

        @Test
        void 중소기업_여부를_모르면_입력_요청() {
            assertThat(rule.evaluate(new Ctx().sme(null).build())).singleElement().satisfies(r -> {
                assertThat(r.ruleCode()).isEqualTo(SmeEmployeeTaxReductionRule.NEED_INPUT_CODE);
                assertThat(r.taskTemplateKey()).isNull();
            });
        }

        @Test
        void 중소기업이_아니면_결과없음() {
            assertThat(rule.evaluate(new Ctx().sme(false).build())).isEmpty();
        }

        @Test
        void 중소기업이면_가능성_안내와_할일() {
            assertThat(rule.evaluate(new Ctx().sme(true).build())).singleElement().satisfies(r -> {
                assertThat(r.severity()).isEqualTo(Severity.WARNING);
                assertThat(r.message()).contains("일 수 있습니다");
                assertThat(r.taskTemplateKey()).isEqualTo("sme-reduction-application");
            });
        }
    }
}
