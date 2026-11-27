package com.gdgoc.otp.infra.ocr;

/**
 * 급여명세서 OCR(이미지/PDF → 표준 필드) 인터페이스.
 *
 * <p>왜 인터페이스로 두나: 서비스 코드는 이 인터페이스만 알고, 실제 구현은 설정으로 갈아 끼운다.</p>
 * <ul>
 *   <li>{@link FakeOcrClient} ({@code otp.ocr.provider: fake}, 기본값): 항상 같은 샘플 결과. 데모·개발·테스트용.</li>
 *   <li>{@link HttpOcrClient} ({@code otp.ocr.provider: http}): 팀 OCR 서버(OCR/ocr-server, FastAPI)를 HTTP로 호출.</li>
 * </ul>
 *
 * <p>구현 시 지켜야 할 것:</p>
 * <ol>
 *   <li>결과의 필드 코드는 PayslipFieldKey.code("base_salary" 등)와 같은 문자열을 쓴다.
 *       모르는 코드를 돌려줘도 서비스가 unmappedItems로 옮기므로 괜찮다.</li>
 *   <li>금액은 월 기준 원 단위 정수. 못 읽으면 0이 아니라 null.</li>
 *   <li>실패(타임아웃, 응답 형식 오류, 명세서가 아님)는 {@link OcrException}으로 던진다.</li>
 *   <li>원본 파일을 외부 서비스에 보낼 때: 주민번호 등 마스킹, 학습 미사용 옵션, 사용자 동의 안내를 확인한다.</li>
 *   <li>원본 파일·비밀번호를 로컬 디스크나 로그에 남기지 않는다.</li>
 * </ol>
 */
public interface OcrClient {

    /**
     * @param request 파일과 부가 정보
     * @return 추출 결과
     * @throws OcrException 추출 실패
     */
    OcrResult extract(OcrRequest request);
}
