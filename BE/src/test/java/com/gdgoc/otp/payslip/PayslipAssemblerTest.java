package com.gdgoc.otp.payslip;

import com.gdgoc.otp.infra.ocr.FakeOcrClient;
import com.gdgoc.otp.infra.ocr.OcrResult;
import com.gdgoc.otp.payslip.domain.FieldStatus;
import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.service.PayslipAssembler;
import com.gdgoc.otp.user.domain.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OCR 결과 → 엔티티 조립 단위 테스트(DB 없음).
 */
class PayslipAssemblerTest {

    PayslipAssembler assembler = new PayslipAssembler();
    User user = User.create("test");

    private FieldStatus statusOf(Payslip payslip, PayslipFieldKey key) {
        return payslip.findField(key).orElseThrow().getStatus();
    }

    @Test
    void 가짜_OCR_샘플은_경고없이_조립된다() {
        OcrResult sample = new FakeOcrClient().extract(new byte[]{1}, "image/png");
        Payslip payslip = assembler.assemble(user, "2026-03", sample, List.of());

        assertThat(payslip.getFields()).hasSize(14);
        assertThat(statusOf(payslip, PayslipFieldKey.BASE_SALARY)).isEqualTo(FieldStatus.OK);
        assertThat(statusOf(payslip, PayslipFieldKey.MEAL_ALLOWANCE)).isEqualTo(FieldStatus.NEEDS_REVIEW);
        assertThat(statusOf(payslip, PayslipFieldKey.BONUS)).isEqualTo(FieldStatus.MISSING);
        // 명절 상여(unmapped)까지 더하면 지급 합계가 맞으므로 경고 없음
        assertThat(payslip.getWarnings()).isEmpty();
        assertThat(payslip.getUnmappedItems()).hasSize(1);
    }

    @Test
    void 모르는_코드는_버리지_않고_unmapped로_옮긴다() {
        OcrResult ocr = new OcrResult("2026-03", null,
                List.of(new OcrResult.Field("holiday_bonus", 50_000, 0.9, "명절수당")), null);
        Payslip payslip = assembler.assemble(user, "2026-03", ocr, List.of());

        assertThat(payslip.getUnmappedItems()).extracting("label").containsExactly("명절수당");
        // 14칸은 모두 MISSING으로 만들어진다
        assertThat(payslip.getFields()).hasSize(14)
                .allSatisfy(field -> assertThat(field.getStatus()).isEqualTo(FieldStatus.MISSING));
    }

    @Test
    void 같은_코드가_두번_오면_첫번째만_쓰고_경고() {
        OcrResult ocr = new OcrResult("2026-03", null, List.of(
                new OcrResult.Field("base_salary", 1_000, 0.9, "기본급"),
                new OcrResult.Field("base_salary", 2_000, 0.9, "기본급(2)")), null);
        Payslip payslip = assembler.assemble(user, "2026-03", ocr, List.of());

        assertThat(payslip.findField(PayslipFieldKey.BASE_SALARY).orElseThrow().getValue()).isEqualTo(1_000);
        assertThat(payslip.getWarnings()).hasSize(1);
    }

    @Test
    void 실수령액이_안맞으면_실수령액을_확인필요로() {
        OcrResult ocr = new OcrResult("2026-03", null, List.of(
                new OcrResult.Field("payment_total", 2_000_000, 0.99, "지급계"),
                new OcrResult.Field("deduction_total", 200_000, 0.99, "공제계"),
                new OcrResult.Field("net_pay", 1_900_000, 0.99, "실수령")), null);
        Payslip payslip = assembler.assemble(user, "2026-03", ocr, List.of());

        assertThat(statusOf(payslip, PayslipFieldKey.NET_PAY)).isEqualTo(FieldStatus.NEEDS_REVIEW);
        assertThat(payslip.getWarnings()).extracting("field").contains("net_pay");
    }

    @Test
    void 항목합이_합계보다_크면_합계를_확인필요로() {
        OcrResult ocr = new OcrResult("2026-03", null, List.of(
                new OcrResult.Field("base_salary", 3_000_000, 0.99, "기본급"),
                new OcrResult.Field("payment_total", 2_000_000, 0.99, "지급계")), null);
        Payslip payslip = assembler.assemble(user, "2026-03", ocr, List.of());

        assertThat(statusOf(payslip, PayslipFieldKey.PAYMENT_TOTAL)).isEqualTo(FieldStatus.NEEDS_REVIEW);
    }

    @Test
    void 범위를_벗어난_신뢰도는_0과_1_사이로_자른다() {
        OcrResult ocr = new OcrResult("2026-03", null,
                List.of(new OcrResult.Field("base_salary", 1_000, 1.7, "기본급")), null);
        Payslip payslip = assembler.assemble(user, "2026-03", ocr, List.of());

        assertThat(payslip.findField(PayslipFieldKey.BASE_SALARY).orElseThrow().getConfidence()).isEqualTo(1.0);
    }
}
