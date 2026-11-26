package com.gdgoc.otp.infra.ocr;

/**
 * 급여명세서 OCR(이미지/PDF → 표준 필드) 인터페이스.
 *
 * <p>왜 인터페이스로 두나: Vision LLM 제공자가 아직 정해지지 않았다.
 * 서비스 코드는 이 인터페이스만 알고, 실제 구현은 갈아 끼운다.</p>
 * <ul>
 *   <li>지금: {@link FakeOcrClient} — 항상 같은 샘플 결과를 돌려준다(데모·개발용).</li>
 *   <li>나중: 예) {@code VisionLlmOcrClient implements OcrClient} 클래스 하나를 추가하고
 *       application.yml의 {@code otp.ocr.provider} 값을 바꾸면 끝.</li>
 * </ul>
 *
 * <p>구현 시 지켜야 할 것 (실제 연동할 때 체크리스트):</p>
 * <ol>
 *   <li>결과의 필드 코드는 PayslipFieldKey.code("base_salary" 등)와 같은 문자열을 쓴다.
 *       모르는 코드를 돌려줘도 서비스가 unmappedItems로 옮기므로 괜찮다.</li>
 *   <li>금액은 월 기준 원 단위 정수. 못 읽으면 0이 아니라 null.</li>
 *   <li>실패(타임아웃, 응답 형식 오류, 명세서가 아님)는 {@link OcrException}으로 던진다.</li>
 *   <li>원본 파일을 외부 서비스에 보낼 때: 주민번호 등 마스킹, 학습 미사용 옵션, 사용자 동의 안내를 확인한다.</li>
 *   <li>원본 파일을 로컬 디스크나 로그에 남기지 않는다.</li>
 * </ol>
 */
public interface OcrClient {

    /**
     * @param content     파일 내용(바이트). 메모리에만 있고 저장하지 않는다
     * @param contentType "image/png", "image/jpeg", "application/pdf" 중 하나(업로드 단계에서 검사 완료)
     * @return 추출 결과
     * @throws OcrException 추출 실패
     */
    OcrResult extract(byte[] content, String contentType);
}
