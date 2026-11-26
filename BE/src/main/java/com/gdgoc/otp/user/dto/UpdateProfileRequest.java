package com.gdgoc.otp.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * 내 정보(점검 입력값) 수정 요청. 세 값 모두 선택이며, 모르면 null로 보낸다.
 *
 * <p>요청 예시:</p>
 * <pre>
 * PUT /api/users/me/profile
 * { "birthYear": 2001, "joinedOn": "2026-03-02", "isSmeEmployee": true }
 * </pre>
 *
 * <p>검증 어노테이션(@Min 등)은 컨트롤러에서 {@code @Valid}를 붙여야 동작한다.
 * 위반하면 GlobalExceptionHandler가 400과 필드별 사유를 돌려준다.
 * null은 검사를 통과한다(값이 있을 때만 범위를 본다).</p>
 *
 * <p>PUT(전체 교체) 의미: 보낸 값으로 세 필드를 모두 덮어쓴다. 빠진 필드는 null이 된다.</p>
 */
public record UpdateProfileRequest(
        @Min(value = 1900, message = "출생연도가 올바르지 않습니다.")
        @Max(value = 2100, message = "출생연도가 올바르지 않습니다.")
        Integer birthYear,

        @PastOrPresent(message = "입사일은 오늘 이전이어야 합니다.")
        LocalDate joinedOn,

        Boolean isSmeEmployee
) {
}
