package com.gdgoc.otp.user.domain;

import com.gdgoc.otp.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 서비스 사용자. ERD의 User 테이블.
 *
 * <p>역할: 로그인 식별, 약관 동의 기록, 감면·공제 점검에 필요한 개인 입력값 보관.</p>
 *
 * <p>테이블 이름이 "users"인 이유: PostgreSQL에서 user는 예약어라
 * 그대로 쓰면 쿼리마다 따옴표로 감싸야 한다.</p>
 *
 * <p>{@code @NoArgsConstructor(access = PROTECTED)}: JPA는 DB에서 읽은 행으로 객체를 만들 때
 * 인자 없는 생성자가 필요하다. 하지만 외부 코드가 빈 User를 함부로 만들지 못하게 protected로 막는다.
 * 새 사용자는 {@link #create(String)}으로만 만든다.</p>
 *
 * <p>Setter를 두지 않는 이유: 아무 데서나 값을 바꾸면 어디서 바뀌었는지 추적하기 어렵다.
 * 대신 updateProfile, agreeToTerms처럼 "무엇을 하는지" 이름이 붙은 메서드로만 바꾼다.</p>
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    /**
     * 기본키. DB가 1, 2, 3… 순서로 자동 증가시킨다(PostgreSQL identity 컬럼).
     * ERD는 문자열(cuid)이지만 Java/JPA에서 가장 흔하고 단순한 Long 자동 증가로 바꿨다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 인증 제공자가 준 사용자 고유 식별자(예: Google 로그인의 sub 값).
     * 인증 방식은 아직 미정이지만, 어떤 방식이든 "외부 ID 문자열 하나"로 사용자를 찾는다.
     * 같은 사람이 두 번 가입되지 않도록 unique.
     */
    @Column(name = "auth_subject", nullable = false, unique = true)
    private String authSubject;

    // ── 감면/공제 점검 입력값 ──
    // 직접 입력받을지 명세서에서 추정할지 팀이 아직 정하지 않아 모두 null 허용이다.
    // null이면 "모름"으로 보고, 규칙 엔진은 해당 점검을 건너뛰거나 입력을 요청한다.

    /** 출생연도. 청년 감면(나이 요건) 판단에 쓴다. 개인정보 최소화를 위해 생년월일 전체가 아니라 연도만 받는다. */
    @Column(name = "birth_year")
    private Integer birthYear;

    /** 입사일. 감면 기간 계산과 "입사 후 할 일" 기한 계산의 기준이다. */
    @Column(name = "joined_on")
    private LocalDate joinedOn;

    /** 중소기업 재직 여부. 명세서만으로는 알기 어려워 사용자 입력이 필요하다. */
    @Column(name = "is_sme_employee")
    private Boolean isSmeEmployee;

    // ── 약관 동의 ──
    // 데모용 단순화: 마지막 동의 한 건만 저장한다(철회·재동의 이력 없음).

    /** 동의한 약관 버전. 예: "2026-11-v1" */
    @Column(name = "terms_version")
    private String termsVersion;

    /** 약관에 동의한 시각. */
    @Column(name = "consented_at")
    private Instant consentedAt;

    /**
     * 새 사용자를 만든다. 처음 로그인했을 때 호출한다.
     *
     * @param authSubject 인증 제공자가 준 고유 식별자
     */
    public static User create(String authSubject) {
        User user = new User();
        user.authSubject = authSubject;
        return user;
    }

    /**
     * 점검용 개인 입력값을 한 번에 바꾼다. 화면의 "내 정보" 저장 버튼에 해당한다.
     * 모르는 값은 null로 넘긴다.
     */
    public void updateProfile(Integer birthYear, LocalDate joinedOn, Boolean isSmeEmployee) {
        this.birthYear = birthYear;
        this.joinedOn = joinedOn;
        this.isSmeEmployee = isSmeEmployee;
    }

    /**
     * 약관 동의를 기록한다.
     *
     * @param termsVersion 동의한 약관 버전
     * @param now          동의 시각. 테스트에서 시각을 고정할 수 있도록 밖에서 받는다.
     */
    public void agreeToTerms(String termsVersion, Instant now) {
        this.termsVersion = termsVersion;
        this.consentedAt = now;
    }

    /** 약관에 한 번이라도 동의했는지. 명세서 업로드 전에 확인한다. */
    public boolean hasConsented() {
        return consentedAt != null;
    }
}
