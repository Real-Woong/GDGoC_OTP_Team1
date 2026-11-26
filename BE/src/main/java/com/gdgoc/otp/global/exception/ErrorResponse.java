package com.gdgoc.otp.global.exception;

import java.time.Instant;
import java.util.List;

/**
 * 모든 에러 응답의 공통 JSON 형식.
 *
 * <p>응답 예시 (입력값 오류):</p>
 * <pre>
 * HTTP 400
 * {
 *   "code": "INVALID_INPUT",
 *   "message": "입력값이 올바르지 않습니다.",
 *   "fieldErrors": [ { "field": "payPeriod", "reason": "YYYY-MM 형식이어야 합니다." } ],
 *   "timestamp": "2026-11-26T07:00:00Z"
 * }
 * </pre>
 *
 * <p>프론트는 {@code code}로 분기하고, {@code message}는 그대로 사용자에게 보여줘도 되는 문구다.
 * 서버 내부 정보(스택 트레이스, SQL)는 절대 담지 않는다.</p>
 *
 * @param code        ErrorCode 이름
 * @param message     사용자에게 보여줄 메시지
 * @param fieldErrors 입력값 검증 실패 시 필드별 사유. 그 외에는 빈 배열
 * @param timestamp   에러 발생 시각(UTC)
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors, Instant timestamp) {

    /** 입력값 검증 실패 한 건. */
    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, List.of(), Instant.now());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage(), fieldErrors, Instant.now());
    }
}
