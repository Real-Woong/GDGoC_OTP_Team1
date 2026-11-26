package com.gdgoc.otp.user.dto;

import com.gdgoc.otp.user.domain.User;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 정보 응답.
 *
 * <p>DTO(Data Transfer Object): API로 주고받는 데이터 모양만 정의한 클래스.
 * 엔티티를 그대로 JSON으로 내보내지 않는 이유:
 * (1) 엔티티 필드를 바꾸면 API 응답이 같이 바뀌어 프론트가 깨진다.
 * (2) authSubject처럼 내보내면 안 되는 값이 실수로 나갈 수 있다.
 * (3) LAZY 연관관계를 JSON으로 바꾸다가 에러나 대량 쿼리가 생길 수 있다.</p>
 *
 * @param hasConsented 약관 동의 여부. 프론트가 동의 화면을 띄울지 판단한다.
 */
public record UserResponse(
        Long id,
        Integer birthYear,
        LocalDate joinedOn,
        Boolean isSmeEmployee,
        String termsVersion,
        Instant consentedAt,
        boolean hasConsented
) {
    /** 엔티티 → DTO 변환. 변환 로직을 DTO 쪽에 두면 서비스 코드가 짧아진다. */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getBirthYear(),
                user.getJoinedOn(),
                user.getIsSmeEmployee(),
                user.getTermsVersion(),
                user.getConsentedAt(),
                user.hasConsented()
        );
    }
}
