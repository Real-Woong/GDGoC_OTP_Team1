package com.gdgoc.otp.finding.domain;

import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.taxrule.domain.TaxRuleVersion;
import com.gdgoc.otp.global.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 점검 결과 1건. ERD의 CheckFinding 테이블.
 *
 * <p>규칙 엔진이 "확인된 명세서 필드 + 사용자 입력값 + 규칙 버전"을 보고 만든다.
 * 예: "중소기업 취업자 소득세 감면 대상일 수 있습니다. 회사에 감면 신청서를 냈는지 확인하세요."</p>
 *
 * <p>한 번 만들어진 결과는 고치지 않는다(수정 메서드 없음, updated_at 컬럼 없음).
 * 다시 점검하면 새 결과를 만든다. 그래야 "그때 어떤 규칙으로 무엇을 안내했는지"가 남는다.</p>
 *
 * <p>BaseTimeEntity를 상속하지 않는 이유: ERD상 createdAt만 있고 updatedAt이 없다.</p>
 */
@Getter
@Entity
@Table(
        name = "check_finding",
        indexes = {
                @Index(name = "idx_check_finding_payslip", columnList = "payslip_id"),
                @Index(name = "idx_check_finding_tax_rule_version", columnList = "tax_rule_version_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 어떤 명세서를 점검한 결과인지. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false)
    private Payslip payslip;

    /** 어떤 규칙 버전으로 판단했는지. 규칙이 바뀌어도 과거 결과를 설명할 수 있게 남긴다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_rule_version_id", nullable = false)
    private TaxRuleVersion taxRuleVersion;

    /**
     * 어떤 규칙이 만든 결과인지 나타내는 코드. 예: "YOUTH_SME_TAX_REDUCTION", "MEAL_ALLOWANCE_LIMIT"
     * 화면 문구와 별개로 프로그램이 결과 종류를 구분할 때 쓴다.
     */
    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    /** 사용자에게 보여줄 안내 문구. 🔒 "식대 200,000원 중 …"처럼 금액이 들어가므로 암호화 저장. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    /**
     * 예상치 여부. 항상 true로 만든다.
     * 우리 서비스는 "점검 안내"일 뿐 확정 판정이 아니다. 최종 확인은 회사·국세청에서 해야 한다.
     * 화면은 이 값이 true면 "예상치" 표시를 붙인다.
     */
    @Column(name = "is_estimate", nullable = false)
    private boolean isEstimate = true;

    /**
     * 점검 결과를 만든다. 규칙 엔진에서만 호출한다.
     * isEstimate는 인자로 받지 않는다. 실수로 확정 결과처럼 만드는 일을 막기 위해서다.
     */
    public static CheckFinding create(Payslip payslip, TaxRuleVersion taxRuleVersion,
                                      String ruleCode, Severity severity, String message) {
        CheckFinding finding = new CheckFinding();
        finding.payslip = payslip;
        finding.taxRuleVersion = taxRuleVersion;
        finding.ruleCode = ruleCode;
        finding.severity = severity;
        finding.message = message;
        return finding;
    }
}
