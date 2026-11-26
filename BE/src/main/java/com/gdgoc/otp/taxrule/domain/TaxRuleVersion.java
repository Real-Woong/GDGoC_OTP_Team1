package com.gdgoc.otp.taxrule.domain;

import com.gdgoc.otp.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 점검에 쓰는 세금 규칙의 한 버전. ERD의 TaxRuleVersion 테이블.
 *
 * <p>왜 버전으로 관리하나: 세법 수치(감면율, 한도 등)는 해마다 바뀌고, 우리가 잘못 넣은 값을 고칠 수도 있다.
 * 점검 결과(CheckFinding)가 "어떤 규칙 버전으로 판단했는지"를 기록해 두면,
 * 나중에 규칙이 바뀌어도 과거 결과가 왜 그렇게 나왔는지 설명할 수 있다.
 * 그래서 한 번 결과에 쓰인 버전의 수치는 고치지 않고, 새 버전을 만든다.</p>
 *
 * <p>주의: 현재 시드 수치는 전부 미검증 더미(DRAFT)다. 실제 안내에 쓰면 안 된다.</p>
 */
@Getter
@Entity
@Table(
        name = "tax_rule_version",
        // 같은 귀속 연도에 같은 버전 이름은 하나만.
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_rule_version_year_version", columnNames = {"tax_year", "version"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxRuleVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 적용 귀속 연도. 예: 2026 */
    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    /** 버전 이름. 예: "2026.1" */
    @Column(name = "version", nullable = false)
    private String version;

    /** 검증 상태. 새로 만든 규칙은 항상 DRAFT로 시작한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RuleStatus status = RuleStatus.DRAFT;

    /**
     * 규칙 수치 묶음. JSON 객체로 저장한다.
     * 예: {"youthTaxReductionRate": 0.9, "youthMaxAge": 34, "mealAllowanceNonTaxableLimit": 200000}
     *
     * <p>키 이름과 구조는 규칙 엔진을 만들면서 정한다. 요건이 많아지면 JSON 하나로는
     * 관리가 어려워지므로 별도 테이블 분리를 검토한다(ERD 문서의 팀 결정 사항).</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters = new HashMap<>();

    /** 수치의 근거가 된 공식 출처 URL(국세청, 법령 등). 검증 전에는 null. */
    @Column(name = "source_url")
    private String sourceUrl;

    /** 공식 출처로 검증을 마친 시각. DRAFT면 null. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** 새 규칙 버전을 DRAFT 상태로 만든다. */
    public static TaxRuleVersion createDraft(Integer taxYear, String version, Map<String, Object> parameters) {
        TaxRuleVersion rule = new TaxRuleVersion();
        rule.taxYear = taxYear;
        rule.version = version;
        rule.parameters = new HashMap<>(parameters);
        return rule;
    }

    /**
     * 공식 출처로 확인을 마쳤다고 표시한다.
     * 출처 없이 VERIFIED가 되는 일이 없도록 sourceUrl을 반드시 받는다.
     */
    public void verify(String sourceUrl, Instant now) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("검증하려면 공식 출처 URL이 필요합니다.");
        }
        this.sourceUrl = sourceUrl;
        this.verifiedAt = now;
        this.status = RuleStatus.VERIFIED;
    }

    public boolean isVerified() {
        return status == RuleStatus.VERIFIED;
    }
}
