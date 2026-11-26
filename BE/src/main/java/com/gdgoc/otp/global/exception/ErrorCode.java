package com.gdgoc.otp.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스에서 쓰는 에러 종류 목록.
 *
 * <p>왜 enum으로 모으나: 에러마다 HTTP 상태, 프론트가 분기할 코드, 기본 메시지를 한 곳에서 관리하려고.
 * 프론트는 메시지 문구가 아니라 {@code code}("PAYSLIP_NOT_FOUND")로 분기해야 한다.
 * 문구는 언제든 바뀔 수 있기 때문이다.</p>
 *
 * <p>새 에러가 필요하면 여기에 한 줄 추가하고 {@code throw new BusinessException(ErrorCode.XXX)}로 던진다.</p>
 */
public enum ErrorCode {

    // ── 공통 ──
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리되었거나 다른 데이터와 충돌합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // ── 사용자 ──
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    TERMS_NOT_AGREED(HttpStatus.FORBIDDEN, "약관 동의가 필요합니다."),

    // ── 명세서 ──
    // 남의 명세서도 403이 아니라 404로 응답한다. 존재 여부를 알려주지 않기 위해서다.
    PAYSLIP_NOT_FOUND(HttpStatus.NOT_FOUND, "명세서를 찾을 수 없습니다."),
    PAYSLIP_FIELD_NOT_FOUND(HttpStatus.NOT_FOUND, "명세서 항목을 찾을 수 없습니다."),
    PAYSLIP_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "확인하지 않은 항목이 있어 점검할 수 없습니다."),
    OCR_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "명세서를 읽지 못했습니다. 더 선명한 이미지로 다시 올려 주세요."),

    // ── 규칙 ──
    TAX_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 연도의 점검 규칙이 없습니다."),

    // ── 할 일 ──
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "할 일을 찾을 수 없습니다."),
    TASK_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "할 일 템플릿을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() { return status; }
    public String getDefaultMessage() { return defaultMessage; }
}
