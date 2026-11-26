package com.gdgoc.otp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 약관 동의 요청.
 *
 * <pre>
 * POST /api/users/me/terms-agreement
 * { "termsVersion": "2026-11-v1" }
 * </pre>
 *
 * @param termsVersion 사용자가 화면에서 본 약관의 버전. 약관이 바뀌면 다시 동의받기 위해 기록한다.
 */
public record AgreeTermsRequest(
        @NotBlank(message = "약관 버전은 필수입니다.")
        @Size(max = 255, message = "약관 버전이 너무 깁니다.")
        String termsVersion
) {
}
