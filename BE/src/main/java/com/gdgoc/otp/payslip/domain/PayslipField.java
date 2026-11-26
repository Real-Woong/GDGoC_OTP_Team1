package com.gdgoc.otp.payslip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 명세서의 표준 필드 1칸. ERD의 PayslipField 테이블.
 *
 * <p>설계 포인트 — "필드는 열(column)이 아니라 행(row)":
 * 기본급, 식대 … 14개를 Payslip의 컬럼 14개로 두지 않고, (key, value) 행으로 저장한다.
 * 덕분에 필드마다 신뢰도·상태·원문 항목명을 따로 담을 수 있고,
 * 필드가 늘어도 테이블 구조를 바꿀 필요가 없다.</p>
 *
 * <p>같은 명세서에 같은 key는 하나만 허용한다(unique 제약).</p>
 */
@Getter
@Entity
@Table(
        name = "payslip_field",
        uniqueConstraints = @UniqueConstraint(name = "uk_payslip_field_payslip_key", columnNames = {"payslip_id", "key"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayslipField {

    /** 신뢰도가 이 값보다 낮으면 NEEDS_REVIEW로 본다(OCR 파트 명세 기준). */
    public static final double REVIEW_THRESHOLD = 0.8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 필드가 속한 명세서. 명세서가 지워지면 필드도 같이 지워진다(Payslip.fields의 cascade). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false)
    private Payslip payslip;

    /**
     * 표준 필드 종류.
     * {@code EnumType.STRING}: DB에 "BASE_SALARY" 같은 이름으로 저장한다.
     * 기본값(ORDINAL)은 0, 1, 2 순서 번호로 저장해서, enum 순서를 바꾸면 기존 데이터의 뜻이 바뀌는 사고가 난다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "key", nullable = false, length = 40)
    private PayslipFieldKey key;

    /**
     * 금액. 월 기준, 원 단위 정수.
     * 읽지 못했으면 0이 아니라 null이다. 0원(실제로 없는 수당)과 "모름"을 구분해야 하기 때문이다.
     */
    @Column(name = "value")
    private Integer value;

    /** OCR이 준 신뢰도(0.0~1.0). 사용자가 직접 입력·수정한 값이면 null. */
    @Column(name = "confidence")
    private Double confidence;

    /** 명세서에 실제로 적혀 있던 항목명. 예: key는 MEAL_ALLOWANCE인데 원문은 "중식대". 화면에서 근거로 보여준다. */
    @Column(name = "source_label")
    private String sourceLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FieldStatus status;

    /** 사용자가 이 값을 확인(또는 수정)했는지. true인 필드만 규칙 엔진으로 넘긴다. */
    @Column(name = "user_confirmed", nullable = false)
    private boolean userConfirmed = false;

    /**
     * OCR 결과로 필드를 만든다.
     * 상태는 직접 받지 않고 값과 신뢰도로 정한다. 상태 판단 기준을 한 곳에 모으기 위해서다.
     *
     * @param key         표준 필드 종류
     * @param value       읽은 금액(못 읽었으면 null)
     * @param confidence  OCR 신뢰도(없으면 null)
     * @param sourceLabel 원문 항목명
     */
    public static PayslipField fromOcr(PayslipFieldKey key, Integer value, Double confidence, String sourceLabel) {
        PayslipField field = new PayslipField();
        field.key = key;
        field.value = value;
        field.confidence = confidence;
        field.sourceLabel = sourceLabel;
        field.status = decideStatus(value, confidence);
        return field;
    }

    /**
     * 값과 신뢰도로 상태를 정한다.
     * - 값이 없으면 MISSING
     * - 신뢰도를 모르거나 0.8 미만이면 NEEDS_REVIEW
     * - 그 외 OK
     */
    private static FieldStatus decideStatus(Integer value, Double confidence) {
        if (value == null) {
            return FieldStatus.MISSING;
        }
        if (confidence == null || confidence < REVIEW_THRESHOLD) {
            return FieldStatus.NEEDS_REVIEW;
        }
        return FieldStatus.OK;
    }

    /**
     * 합계 검증 등 OCR 이후 검사에서 문제가 발견되면 확인 필요 상태로 바꾼다.
     * 값이 없는(MISSING) 필드는 그대로 둔다.
     */
    public void markNeedsReview() {
        if (status != FieldStatus.MISSING) {
            this.status = FieldStatus.NEEDS_REVIEW;
        }
    }

    /**
     * 사용자가 화면에서 값을 확인했다. 수정한 경우 새 값을 넘긴다.
     *
     * <p>사람이 확인한 값이므로 상태를 OK로 바꾼다. 단, 사용자가 "이 항목은 없음"으로 확인해
     * null을 넘기면 MISSING으로 둔다. 값을 고쳤다면 OCR 신뢰도는 더 이상 의미가 없으니 지운다.</p>
     *
     * @param confirmedValue 사용자가 확정한 금액(원). 원래 값 그대로면 같은 값을 넘긴다.
     */
    public void confirm(Integer confirmedValue) {
        boolean changed = (value == null) ? confirmedValue != null : !value.equals(confirmedValue);
        if (changed) {
            this.value = confirmedValue;
            this.confidence = null;
        }
        this.status = (confirmedValue == null) ? FieldStatus.MISSING : FieldStatus.OK;
        this.userConfirmed = true;
    }

    /**
     * Payslip.addField에서만 호출한다(같은 패키지에서만 보이도록 접근제어자 생략).
     * 양방향 관계의 "필드 → 명세서" 쪽을 채운다.
     */
    void assignPayslip(Payslip payslip) {
        this.payslip = payslip;
    }
}
