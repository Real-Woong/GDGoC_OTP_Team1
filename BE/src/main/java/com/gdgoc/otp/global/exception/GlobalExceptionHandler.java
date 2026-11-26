package com.gdgoc.otp.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 모든 컨트롤러에서 던져진 예외를 한 곳에서 받아 {@link ErrorResponse} JSON으로 바꾼다.
 *
 * <p>{@code @RestControllerAdvice}: 모든 @RestController에 공통으로 적용되는 "예외 처리 전담 클래스".
 * 컨트롤러나 서비스에서 예외가 나면, 스프링이 여기서 타입이 맞는 @ExceptionHandler 메서드를 찾아 호출한다.
 * 덕분에 각 컨트롤러는 정상 흐름만 작성하면 된다.</p>
 *
 * <p>처리 순서: 더 구체적인 예외 타입의 핸들러가 우선한다. 어디에도 안 걸린 예외는
 * 마지막 {@link #handleUnexpected}가 받아 500으로 응답한다.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 우리가 의도해서 던진 비즈니스 예외. ErrorCode에 정해진 상태 코드로 응답한다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        // 예상한 상황이라 스택 트레이스 없이 한 줄만 남긴다.
        log.info("BusinessException: {} - {}", code, e.getMessage());
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
    }

    /**
     * 요청 본문(@RequestBody @Valid) 검증 실패.
     * 예: @NotBlank인 필드가 비어 있음 → 어떤 필드가 왜 틀렸는지 목록으로 돌려준다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    /**
     * 잘못된 요청 형식들.
     * - HttpMessageNotReadableException: JSON 문법 오류, 숫자 자리에 문자 등
     * - MethodArgumentTypeMismatchException: /payslips/abc 처럼 경로 변수 타입이 안 맞음
     * - MissingServletRequestParameterException: 필수 쿼리 파라미터 누락
     * - MissingServletRequestPartException: 파일 업로드 요청에 file 파트가 없음
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        log.info("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getDefaultMessage()));
    }

    /** 업로드 파일이 application.yml의 max-file-size(10MB)를 넘음. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "파일이 너무 큽니다. 10MB 이하로 올려 주세요."));
    }

    /** 없는 URL. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage()));
    }

    /** URL은 맞는데 HTTP 메서드가 틀림(예: GET만 있는데 POST로 요청). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405)
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "지원하지 않는 요청 방식입니다."));
    }

    /**
     * DB 제약 위반(unique, CHECK, FK).
     * 예: 동시에 두 요청이 같은 할 일을 만들려다 unique 제약에 걸림.
     * 서비스에서 미리 확인하더라도 동시 요청은 막을 수 없어서, DB 제약이 마지막 방어선이다.
     * 제약 이름이나 SQL은 내부 정보라 응답에는 담지 않고 로그에만 남긴다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.getStatus())
                .body(ErrorResponse.of(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getDefaultMessage()));
    }

    /**
     * 위에서 처리하지 못한 모든 예외 = 예상하지 못한 버그.
     * 원인 추적을 위해 스택 트레이스 전체를 ERROR로 남기고, 사용자에게는 일반 문구만 준다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
