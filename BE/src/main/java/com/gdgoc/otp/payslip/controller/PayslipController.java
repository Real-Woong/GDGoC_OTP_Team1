package com.gdgoc.otp.payslip.controller;

import com.gdgoc.otp.global.auth.CurrentUser;
import com.gdgoc.otp.global.auth.LoginUser;
import com.gdgoc.otp.payslip.dto.ConfirmFieldsRequest;
import com.gdgoc.otp.payslip.dto.PayslipDetailResponse;
import com.gdgoc.otp.payslip.dto.PayslipSummaryResponse;
import com.gdgoc.otp.payslip.service.PayslipService;
import com.gdgoc.otp.payslip.service.PayslipUploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 명세서 API.
 *
 * <p>URL 설계: 자원(명세서)을 복수형 명사로 두고, 동작은 HTTP 메서드로 표현한다.</p>
 * <ul>
 *   <li>GET   /api/payslips              내 명세서 목록</li>
 *   <li>GET   /api/payslips/{id}         상세(확인 화면)</li>
 *   <li>PATCH /api/payslips/{id}/fields  필드 확인·수정 (일부만 바꾸므로 PATCH)</li>
 *   <li>POST  /api/payslips              업로드(multipart) → OCR → 저장, 201</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/payslips")
public class PayslipController {

    private final PayslipService payslipService;
    private final PayslipUploadService payslipUploadService;

    public PayslipController(PayslipService payslipService, PayslipUploadService payslipUploadService) {
        this.payslipService = payslipService;
        this.payslipUploadService = payslipUploadService;
    }

    /**
     * 명세서 업로드.
     *
     * <p>요청 형식은 JSON이 아니라 multipart/form-data(파일 전송용)다.</p>
     * <pre>
     * POST /api/payslips
     * Content-Type: multipart/form-data
     *   file      = (명세서 이미지/PDF, 필수)
     *   payPeriod   = "2026-03" (선택. 없으면 OCR이 읽은 값 사용)
     *   pdfPassword = "990101"  (선택. 비밀번호 PDF일 때만. 처음엔 없이 보내고 428 PDF_PASSWORD_REQUIRED를 받으면 다시 보낸다)
     * </pre>
     * <p>응답은 상세 조회와 같은 형식이다. 프론트는 이 응답으로 바로 확인 화면을 그린다.</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PayslipDetailResponse upload(@CurrentUser LoginUser loginUser,
                                        @RequestPart("file") MultipartFile file,
                                        @RequestParam(required = false) String payPeriod,
                                        @RequestParam(required = false) String pdfPassword) {
        return payslipUploadService.upload(loginUser.id(), file, payPeriod, pdfPassword);
    }

    @GetMapping
    public List<PayslipSummaryResponse> getMyPayslips(@CurrentUser LoginUser loginUser) {
        return payslipService.getMyPayslips(loginUser.id());
    }

    /** {@code @PathVariable}: URL의 {id} 부분을 Long으로 받는다. 숫자가 아니면 400. */
    @GetMapping("/{payslipId}")
    public PayslipDetailResponse getMyPayslip(@CurrentUser LoginUser loginUser,
                                              @PathVariable Long payslipId) {
        return payslipService.getMyPayslip(loginUser.id(), payslipId);
    }

    @PatchMapping("/{payslipId}/fields")
    public PayslipDetailResponse confirmFields(@CurrentUser LoginUser loginUser,
                                               @PathVariable Long payslipId,
                                               @Valid @RequestBody ConfirmFieldsRequest request) {
        return payslipService.confirmFields(loginUser.id(), payslipId, request);
    }
}
