package com.gdgoc.otp.infra.ocr;

/**
 * OCR 실패. 구현체가 던지고, 업로드 서비스(PayslipUploadService)가 받아서 사용자용 에러로 바꾼다.
 *
 * <p>두 종류의 정보를 따로 담는다.</p>
 * <ul>
 *   <li>{@code getMessage()}: 개발자용 원인(타임아웃, 응답 형식 오류 등). 로그에만 남고 사용자에게 나가지 않는다.</li>
 *   <li>{@link #getCode()}, {@link #getUserMessage()}: OCR 서버가 준 에러 코드와 "사용자에게 그대로 보여도 되는" 문장.
 *       예: code=TOO_MANY_PAGES, userMessage="PDF는 3페이지까지 올릴 수 있어요."
 *       OCR 서버가 이런 정보를 주지 않은 실패(타임아웃, 서버 다운)는 둘 다 null이고,
 *       이때 사용자에게는 일반 문구가 나간다.</li>
 * </ul>
 */
public class OcrException extends RuntimeException {

    /** OCR 서버 에러 코드(예: PDF_PASSWORD_REQUIRED). 없으면 null. */
    private final String code;

    /** 사용자에게 보여 줘도 되는 문장. 없으면 null. */
    private final String userMessage;

    public OcrException(String message) {
        this(message, null, null, null);
    }

    public OcrException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public OcrException(String message, String code, String userMessage) {
        this(message, code, userMessage, null);
    }

    private OcrException(String message, String code, String userMessage, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.userMessage = userMessage;
    }

    public String getCode() {
        return code;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
