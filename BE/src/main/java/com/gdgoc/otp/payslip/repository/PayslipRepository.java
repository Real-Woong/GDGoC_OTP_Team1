package com.gdgoc.otp.payslip.repository;

import com.gdgoc.otp.payslip.domain.Payslip;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Payslip 테이블 접근.
 *
 * <p>PayslipField는 별도 Repository를 두지 않는다. 필드는 항상 명세서를 통해서만 저장·수정·삭제한다
 * (Payslip.fields의 cascade). 이렇게 하면 "같은 key 중복 금지" 같은 규칙이 Payslip 한 곳에서만 지켜진다.</p>
 *
 * <p>소유권 확인 방식: 조회할 때 항상 id와 userId를 같이 조건으로 건다.
 * 남의 명세서 id로 요청하면 "없음"(404)이 되어, 그 명세서가 존재하는지조차 알려주지 않는다.
 * 메서드 이름의 {@code User_Id}는 "user 필드의 id"라는 뜻이다(밑줄로 중첩 속성을 명시).</p>
 */
public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    /**
     * 내 명세서 1건을 필드까지 한 번에 가져온다.
     *
     * <p>{@code @EntityGraph(attributePaths = "fields")}: fields는 LAZY라 원래는 접근할 때 쿼리가 한 번 더 나간다.
     * 상세 화면은 필드를 항상 쓰므로, 조인으로 한 번에 가져와 쿼리 수를 줄인다.</p>
     */
    @EntityGraph(attributePaths = "fields")
    Optional<Payslip> findWithFieldsByIdAndUser_Id(Long id, Long userId);

    /** 내 명세서 목록. 최근 귀속월이 위로 오게 정렬한다("2026-03" 형식이라 문자열 정렬 = 날짜 정렬). */
    List<Payslip> findByUser_IdOrderByPayPeriodDesc(Long userId);

    /** 같은 귀속월 명세서가 이미 있는지. 중복 업로드 경고에 쓸 수 있다. */
    boolean existsByUser_IdAndPayPeriod(Long userId, String payPeriod);
}
