package com.gdgoc.otp.global.exception;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. 서비스 코드에서 던진다.
 *
 * <p>예: {@code throw new BusinessException(ErrorCode.PAYSLIP_NOT_FOUND);}</p>
 *
 * <p>RuntimeException(unchecked)을 상속하는 이유: 메서드마다 throws를 적지 않아도 되고,
 * 스프링 @Transactional은 기본적으로 RuntimeException이 나면 롤백한다.
 * 즉 이 예외를 던지면 그 요청에서 한 DB 변경이 자동으로 취소된다.</p>
 *
 * <p>던진 예외는 {@link GlobalExceptionHandler}가 받아서 공통 JSON 에러 응답으로 바꾼다.
 * 그래서 컨트롤러에서 try-catch를 쓸 필요가 없다.</p>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 기본 메시지를 그대로 쓸 때. */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 상황에 맞는 구체적인 메시지를 줄 때.
     * 예: new BusinessException(ErrorCode.TAX_RULE_NOT_FOUND, "2027년 점검 규칙이 없습니다.")
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
