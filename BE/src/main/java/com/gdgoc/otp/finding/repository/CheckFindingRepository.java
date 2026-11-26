package com.gdgoc.otp.finding.repository;

import com.gdgoc.otp.finding.domain.CheckFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * CheckFinding 테이블 접근.
 *
 * <p>점검을 다시 하면 이전 결과를 지우지 않고 새 결과를 추가한다(이력 보존).
 * 그래서 "가장 최근 점검 결과만" 보여주려면 서비스에서 마지막 점검 회차를 골라야 한다.
 * 회차 구분이 필요해지면 CheckRun 같은 테이블을 추가하는 것을 검토한다.</p>
 */
public interface CheckFindingRepository extends JpaRepository<CheckFinding, Long> {

    /** 명세서 하나의 점검 결과 전체. 만든 순서대로. */
    List<CheckFinding> findByPayslip_IdOrderByIdAsc(Long payslipId);
}
