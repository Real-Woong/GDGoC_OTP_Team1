package com.gdgoc.otp.payslip.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 급여명세서 OCR 표준 필드 14개.
 *
 * <p>회사마다 명세서 항목 이름이 다르다("기본급", "기본 급여", "Base Pay" 등).
 * OCR 단계에서 이 14개 중 하나로 맞춰서(매핑해서) 저장한다.
 * 어디에도 맞지 않는 항목은 Payslip.unmappedItems에 원문 그대로 남긴다.</p>
 *
 * <p>DB에는 enum 이름(BASE_SALARY 등)이 문자열로 저장된다.
 * {@link #code}는 OCR 모듈·프론트와 주고받는 JSON 키(snake_case)이고,
 * prisma/schema.prisma의 enum 값과 같다.</p>
 *
 * <p>모든 금액은 "월 기준, 원 단위 정수"다.</p>
 */
public enum PayslipFieldKey {

    // ── 지급 항목 (회사가 주는 돈) ──
    BASE_SALARY("base_salary", "기본급", Category.PAYMENT),
    MEAL_ALLOWANCE("meal_allowance", "식대", Category.PAYMENT),
    CAR_ALLOWANCE("car_allowance", "자가운전보조금", Category.PAYMENT),
    OVERTIME_PAY("overtime_pay", "연장근로수당", Category.PAYMENT),
    BONUS("bonus", "상여금", Category.PAYMENT),
    PAYMENT_TOTAL("payment_total", "지급액 합계", Category.PAYMENT_TOTAL),

    // ── 공제 항목 (월급에서 빠지는 돈) ──
    NATIONAL_PENSION("national_pension", "국민연금", Category.DEDUCTION),
    HEALTH_INSURANCE("health_insurance", "건강보험", Category.DEDUCTION),
    LONG_TERM_CARE("long_term_care", "장기요양보험", Category.DEDUCTION),
    EMPLOYMENT_INSURANCE("employment_insurance", "고용보험", Category.DEDUCTION),
    INCOME_TAX("income_tax", "소득세", Category.DEDUCTION),
    LOCAL_INCOME_TAX("local_income_tax", "지방소득세", Category.DEDUCTION),
    DEDUCTION_TOTAL("deduction_total", "공제액 합계", Category.DEDUCTION_TOTAL),

    // ── 결과 ──
    NET_PAY("net_pay", "실수령액", Category.NET);

    /**
     * 필드의 성격. 합계 검증에 쓴다.
     * 예: PAYMENT 항목을 모두 더하면 PAYMENT_TOTAL과 같아야 하고,
     *     PAYMENT_TOTAL - DEDUCTION_TOTAL = NET 이어야 한다.
     * (단, 명세서에 14개 외 항목이 있으면 합이 안 맞을 수 있으므로 경고로만 다룬다.)
     */
    public enum Category { PAYMENT, PAYMENT_TOTAL, DEDUCTION, DEDUCTION_TOTAL, NET }

    /** OCR·API에서 쓰는 snake_case 키. 예: "base_salary" */
    private final String code;
    /** 화면에 보여줄 한글 이름. 예: "기본급" */
    private final String koreanName;
    private final Category category;

    PayslipFieldKey(String code, String koreanName, Category category) {
        this.code = code;
        this.koreanName = koreanName;
        this.category = category;
    }

    public String getCode() { return code; }
    public String getKoreanName() { return koreanName; }
    public Category getCategory() { return category; }

    /**
     * OCR 결과의 키 문자열("base_salary")로 enum을 찾는다.
     * 모르는 키면 예외 대신 빈 Optional을 돌려준다.
     * 호출하는 쪽에서 unmappedItems로 보낼지 결정하게 하기 위해서다.
     */
    public static Optional<PayslipFieldKey> fromCode(String code) {
        return Arrays.stream(values())
                .filter(key -> key.code.equals(code))
                .findFirst();
    }
}
