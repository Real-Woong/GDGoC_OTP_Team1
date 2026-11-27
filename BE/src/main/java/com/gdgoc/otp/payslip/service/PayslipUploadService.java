package com.gdgoc.otp.payslip.service;

import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.global.exception.ErrorCode;
import com.gdgoc.otp.infra.ocr.OcrClient;
import com.gdgoc.otp.infra.ocr.OcrException;
import com.gdgoc.otp.infra.ocr.OcrRequest;
import com.gdgoc.otp.infra.ocr.OcrResult;
import com.gdgoc.otp.payslip.dto.PayslipDetailResponse;
import com.gdgoc.otp.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 명세서 업로드 흐름을 조율한다.
 *
 * <pre>
 * 1. 약관 동의 확인 (안 했으면 403)
 * 2. 파일 형식 검사 (PNG/JPEG/PDF가 아니면 400)
 * 3. OCR 호출            ← 1장에 약 9초(OCR 팀 측정). provider가 http면 팀 OCR 서버, fake면 고정 샘플
 * 4. 귀속월 결정 (사용자 입력 > OCR 결과, 둘 다 없으면 400)
 * 5. 저장 (PayslipService.saveUploaded, 여기서만 트랜잭션)
 * </pre>
 *
 * <p>왜 이 클래스에는 {@code @Transactional}이 없나:
 * 트랜잭션이 열려 있는 동안 DB 연결 하나를 계속 붙잡는다. OCR을 기다리는 15초 동안 연결을 붙잡으면
 * 동시 업로드가 몇 개만 몰려도 연결 풀(기본 10개)이 바닥나 다른 API까지 멈춘다.
 * 그래서 오래 걸리는 OCR은 트랜잭션 밖에서 하고, 짧은 저장만 다른 클래스(PayslipService)의 트랜잭션에서 한다.</p>
 *
 * <p>왜 저장 메서드를 같은 클래스에 두지 않나: 스프링의 @Transactional은 "다른 빈을 통해 호출될 때"만 동작한다.
 * 같은 클래스 안에서 this.save()로 부르면 트랜잭션이 적용되지 않는다(self-invocation 문제).</p>
 *
 * <p>원본 파일: 메모리(byte[])에서만 쓰고 저장하지 않는다. 스프링이 업로드 처리 중 만든 임시 파일은
 * 요청이 끝나면 자동으로 지워진다.</p>
 *
 * <p>비밀번호 PDF: 처음 요청에 pdfPassword가 없으면 OCR 서버가 428을 주고, 이 서비스가
 * PDF_PASSWORD_REQUIRED로 바꿔 FE에 돌려준다. FE는 같은 파일에 pdfPassword를 붙여 다시 올린다.
 * BE는 원본을 저장하지 않으므로 "파일을 다시 보내는" 쪽은 FE다. 비밀번호는 OCR 서버로 넘기기만 하고 저장·로그하지 않는다.</p>
 *
 * <p>TODO: 9초 동기 응답이 길면 비동기(job_id 발급 후 결과 조회) 방식을 검토한다.
 * 회의 안건 "동기 vs 비동기"와 연결된다.</p>
 */
@Service
public class PayslipUploadService {

    private static final Logger log = LoggerFactory.getLogger(PayslipUploadService.class);
    private static final Pattern PAY_PERIOD = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private final UserService userService;
    private final UploadedFileValidator fileValidator;
    private final OcrClient ocrClient;
    private final PayslipService payslipService;

    public PayslipUploadService(UserService userService, UploadedFileValidator fileValidator,
                                OcrClient ocrClient, PayslipService payslipService) {
        this.userService = userService;
        this.fileValidator = fileValidator;
        this.ocrClient = ocrClient;
        this.payslipService = payslipService;
    }

    /**
     * @param payPeriodInput 사용자가 직접 입력한 귀속월(선택). 있으면 OCR 결과보다 우선한다
     * @param pdfPassword    비밀번호 PDF의 비밀번호(선택)
     */
    public PayslipDetailResponse upload(Long userId, MultipartFile file, String payPeriodInput, String pdfPassword) {
        // 1. 약관 동의: 명세서를 외부 OCR에 보내기 전에 반드시 동의를 받아야 한다.
        if (!userService.getUser(userId).hasConsented()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }

        // 사용자 입력 귀속월은 OCR을 부르기 전에 검사한다(형식이 틀렸는데 15초 기다리게 하지 않도록).
        if (payPeriodInput != null && !payPeriodInput.isBlank() && !PAY_PERIOD.matcher(payPeriodInput).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "귀속월은 YYYY-MM 형식이어야 합니다.");
        }

        // 2. 파일 검사
        byte[] content = readBytes(file);
        UploadedFileValidator.FileType fileType = fileValidator.validate(content);

        // 3. OCR (트랜잭션 밖)
        // 사용자가 귀속월을 입력했으면 그 연도를 OCR에 힌트로 준다(명세서에 연도가 없을 때 요율 검사에 쓰임).
        Integer payYear = hasText(payPeriodInput) ? Integer.valueOf(payPeriodInput.substring(0, 4)) : null;
        OcrResult ocrResult;
        try {
            ocrResult = ocrClient.extract(new OcrRequest(content, fileType.contentType(), pdfPassword, payYear));
        } catch (OcrException e) {
            log.warn("OCR 실패: {}", e.getMessage());
            throw toBusinessException(e);
        }

        // 4. 귀속월 결정
        String payPeriod = decidePayPeriod(payPeriodInput, ocrResult.payPeriod());

        // 5. 저장 (트랜잭션 안)
        return payslipService.saveUploaded(userId, payPeriod, ocrResult);
    }

    /**
     * OCR 실패를 사용자용 에러로 바꾼다.
     * <ul>
     *   <li>비밀번호 관련: FE가 비밀번호 입력창을 띄울 수 있도록 전용 코드(428)</li>
     *   <li>OCR 서버가 사용자용 문장을 준 경우(예: "PDF는 3페이지까지"): OCR_FAILED + 그 문장</li>
     *   <li>그 외(타임아웃, 서버 장애): OCR_FAILED + 일반 문구. 내부 원인은 응답에 넣지 않는다</li>
     * </ul>
     */
    private BusinessException toBusinessException(OcrException e) {
        if ("PDF_PASSWORD_REQUIRED".equals(e.getCode())) {
            return new BusinessException(ErrorCode.PDF_PASSWORD_REQUIRED);
        }
        if ("PDF_PASSWORD_INVALID".equals(e.getCode())) {
            return new BusinessException(ErrorCode.PDF_PASSWORD_INVALID);
        }
        if (hasText(e.getUserMessage())) {
            return new BusinessException(ErrorCode.OCR_FAILED, e.getUserMessage());
        }
        return new BusinessException(ErrorCode.OCR_FAILED);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "파일을 읽지 못했습니다.");
        }
    }

    /** 사용자 입력이 있으면 그것을, 없으면 OCR이 읽은 값을 쓴다. OCR 값도 형식이 틀리면 없는 것으로 본다. */
    private String decidePayPeriod(String input, String fromOcr) {
        if (input != null && !input.isBlank()) {
            return input;
        }
        if (fromOcr != null && PAY_PERIOD.matcher(fromOcr).matches()) {
            return fromOcr;
        }
        throw new BusinessException(ErrorCode.PAY_PERIOD_REQUIRED);
    }
}
