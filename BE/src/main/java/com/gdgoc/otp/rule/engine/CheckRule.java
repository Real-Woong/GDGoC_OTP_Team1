package com.gdgoc.otp.rule.engine;

import java.util.List;

/**
 * 점검 규칙 하나. 새 규칙을 추가하려면 이 인터페이스를 구현한 클래스에 {@code @Component}를 붙이면 끝이다.
 * {@link RuleEngine}이 스프링에 등록된 모든 CheckRule을 자동으로 모아 실행한다.
 *
 * <p>규칙 작성 원칙:</p>
 * <ul>
 *   <li>판단에 필요한 값이 없으면(사용자 입력 없음, 규칙 수치 미검증) 추측하지 말고
 *       "확인이 필요하다"는 안내를 내거나 아무것도 내지 않는다.</li>
 *   <li>문구는 단정하지 않는다. 우리는 점검 안내일 뿐이고, 최종 판단은 회사·국세청이 한다.</li>
 *   <li>DB를 직접 조회하지 않는다. 필요한 값은 {@link CheckContext}에 추가한다.</li>
 * </ul>
 */
public interface CheckRule {

    /**
     * @return 판단 결과 목록. 해당 사항이 없으면 빈 리스트
     */
    List<RuleResult> evaluate(CheckContext context);
}
