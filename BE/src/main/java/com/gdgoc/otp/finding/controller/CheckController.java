package com.gdgoc.otp.finding.controller;

import com.gdgoc.otp.finding.dto.CheckResultResponse;
import com.gdgoc.otp.finding.dto.FindingResponse;
import com.gdgoc.otp.finding.service.CheckService;
import com.gdgoc.otp.global.auth.CurrentUser;
import com.gdgoc.otp.global.auth.LoginUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 점검 API. 명세서의 하위 자원으로 둔다.
 * <ul>
 *   <li>POST /api/payslips/{id}/check     점검 실행 → 201 Created</li>
 *   <li>GET  /api/payslips/{id}/findings  점검 결과 이력</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/payslips/{payslipId}")
public class CheckController {

    private final CheckService checkService;

    public CheckController(CheckService checkService) {
        this.checkService = checkService;
    }

    /** 점검은 새 결과(CheckFinding)를 "만드는" 동작이라 POST, 성공 시 201. */
    @PostMapping("/check")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckResultResponse check(@CurrentUser LoginUser loginUser, @PathVariable Long payslipId) {
        return checkService.check(loginUser.id(), payslipId);
    }

    @GetMapping("/findings")
    public List<FindingResponse> getFindings(@CurrentUser LoginUser loginUser, @PathVariable Long payslipId) {
        return checkService.getFindings(loginUser.id(), payslipId);
    }
}
