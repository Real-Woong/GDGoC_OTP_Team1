package com.gdgoc.otp.infra.ocr;

/**
 * OCR에 보낼 요청 한 건.
 *
 * <p>처음에는 파일과 형식만 넘겼는데, 실제 OCR 서버(OCR/docs/api/ocr-api.md)가
 * 비밀번호 PDF와 귀속 연도 힌트도 받기 때문에 값을 하나로 묶었다.
 * 나중에 값이 더 늘어나도 OcrClient 메서드 모양은 그대로 둘 수 있다.</p>
 *
 * @param content     파일 내용(바이트). 메모리에만 있고 저장하지 않는다
 * @param contentType "image/png", "image/jpeg", "application/pdf" 중 하나(업로드 단계에서 검사 완료)
 * @param pdfPassword 비밀번호가 걸린 PDF일 때만. 없으면 null
 * @param payYear     귀속 연도 힌트(예: 2026). OCR이 명세서에서 연도를 못 찾았을 때 요율 검사에 쓴다. 없으면 null
 */
public record OcrRequest(byte[] content, String contentType, String pdfPassword, Integer payYear) {

    /** 비밀번호·연도 없이 파일만 보낼 때. */
    public static OcrRequest of(byte[] content, String contentType) {
        return new OcrRequest(content, contentType, null, null);
    }

    /**
     * 기본 toString은 모든 값을 출력한다. 실수로 이 객체를 로그에 찍어도
     * 비밀번호(대개 생년월일이라 개인정보)와 파일 내용이 남지 않도록 직접 정의한다.
     */
    @Override
    public String toString() {
        return "OcrRequest[%d bytes, %s, pdfPassword=%s, payYear=%s]".formatted(
                content == null ? 0 : content.length, contentType, pdfPassword == null ? "없음" : "***", payYear);
    }
}
