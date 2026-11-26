package com.gdgoc.otp.taxrule.repository;

import com.gdgoc.otp.taxrule.domain.RuleStatus;
import com.gdgoc.otp.taxrule.domain.TaxRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * TaxRuleVersion 테이블 접근.
 */
public interface TaxRuleVersionRepository extends JpaRepository<TaxRuleVersion, Long> {

    Optional<TaxRuleVersion> findByTaxYearAndVersion(Integer taxYear, String version);

    /**
     * 해당 연도에서 가장 최근에 만든 규칙 버전(상태 무관).
     * 데모에서는 DRAFT 규칙밖에 없으므로 이것을 쓴다.
     * id가 클수록 나중에 만든 것이다(자동 증가).
     */
    Optional<TaxRuleVersion> findTopByTaxYearOrderByIdDesc(Integer taxYear);

    /**
     * 해당 연도에서 가장 최근의 특정 상태 규칙. 실서비스에서는 VERIFIED만 써야 한다.
     * 예: findTopByTaxYearAndStatusOrderByIdDesc(2026, RuleStatus.VERIFIED)
     */
    Optional<TaxRuleVersion> findTopByTaxYearAndStatusOrderByIdDesc(Integer taxYear, RuleStatus status);
}
