package com.gdgoc.otp.payslip.controller;

import com.gdgoc.otp.global.auth.CurrentUser;
import com.gdgoc.otp.global.auth.LoginUser;
import com.gdgoc.otp.payslip.dto.ConfirmFieldsRequest;
import com.gdgoc.otp.payslip.dto.PayslipDetailResponse;
import com.gdgoc.otp.payslip.dto.PayslipSummaryResponse;
import com.gdgoc.otp.payslip.service.PayslipService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 명세서 API.
 *
 * <p>URL 설계: 자원(명세서)을 복수형 명사로 두고, 동작은 HTTP 메서드로 표현한다.</p>
 * <ul>
 *   <li>GET   /api/payslips              내 명세서 목록</li>
 *   <li>GET   /api/payslips/{id}         상세(확인 화면)</li>
 *   <li>PATCH /api/payslips/{id}/fields  필드 확인·수정 (일부만 바꾸므로 PATCH)</li>
 *   <li>POST  /api/payslips              업로드 → 6단계에서 추가</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/payslips")
public class PayslipController {

    private final PayslipService payslipService;

    public PayslipController(PayslipService payslipService) {
        this.payslipService = payslipService;
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
