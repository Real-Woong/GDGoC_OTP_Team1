package com.gdgoc.otp.infra.ocr;

/**
 * OCR 실패. 구현체가 던지고, 업로드 서비스가 받아서 사용자용 에러(OCR_FAILED, 422)로 바꾼다.
 *
 * <p>message에는 원인(타임아웃, 응답 형식 오류 등)을 적는다. 이 메시지는 로그에만 남고
 * 사용자에게는 일반 문구("명세서를 읽지 못했습니다")가 나간다.</p>
 */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
