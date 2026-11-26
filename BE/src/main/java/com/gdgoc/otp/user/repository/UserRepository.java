package com.gdgoc.otp.user.repository;

import com.gdgoc.otp.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 테이블 접근.
 *
 * <p>Spring Data JPA 기본 원리: 인터페이스만 선언하면 스프링이 실행 시점에 구현체를 만들어 준다.
 * {@code JpaRepository<User, Long>}을 상속하면 save, findById, findAll, delete 등이 공짜로 생긴다.
 * (제네릭 인자: 엔티티 타입, 기본키 타입)</p>
 *
 * <p>메서드 이름 규칙으로 쿼리를 만든다. 예: findByAuthSubject →
 * {@code select u from User u where u.authSubject = ?1}.
 * 이름에 오타가 있으면 앱이 켜질 때 바로 에러가 나므로, contextLoads 테스트로 잡을 수 있다.</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 로그인할 때 인증 제공자가 준 식별자로 사용자를 찾는다. 없으면 신규 가입 처리. */
    Optional<User> findByAuthSubject(String authSubject);
}
