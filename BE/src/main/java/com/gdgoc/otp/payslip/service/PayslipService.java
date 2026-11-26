package com.gdgoc.otp.payslip.service;

import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.global.exception.ErrorCode;
import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.dto.ConfirmFieldsRequest;
import com.gdgoc.otp.payslip.dto.PayslipDetailResponse;
import com.gdgoc.otp.payslip.dto.PayslipSummaryResponse;
import com.gdgoc.otp.payslip.repository.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 명세서 조회와 필드 확인.
 *
 * <p>업로드(OCR)는 6단계에서 추가한다. 지금은 이미 저장된 명세서를 보고 확인하는 기능만 있다.</p>
 */
@Service
@Transactional(readOnly = true)
public class PayslipService {

    private final PayslipRepository payslipRepository;

    public PayslipService(PayslipRepository payslipRepository) {
        this.payslipRepository = payslipRepository;
    }

    /** 내 명세서 목록(최근 귀속월 먼저). */
    public List<PayslipSummaryResponse> getMyPayslips(Long userId) {
        return payslipRepository.findByUser_IdOrderByPayPeriodDesc(userId).stream()
                .map(PayslipSummaryResponse::from)
                .toList();
    }

    /** 내 명세서 상세. 남의 명세서면 404. */
    public PayslipDetailResponse getMyPayslip(Long userId, Long payslipId) {
        return PayslipDetailResponse.from(getOwnedPayslip(userId, payslipId));
    }

    /**
     * 필드를 확인(및 수정)한다.
     *
     * <p>요청 전체를 먼저 검사한 뒤에 변경한다. 중간에 잘못된 항목이 있으면 아무것도 바꾸지 않는다.
     * (예외가 나면 @Transactional이 롤백하므로 사실 중간에 바꿔도 DB에는 안 남지만,
     * 검사와 변경을 나누면 코드 의도가 더 분명하다.)</p>
     *
     * @throws BusinessException 모르는 필드 코드, 같은 필드 중복, 명세서에 없는 필드
     */
    @Transactional
    public PayslipDetailResponse confirmFields(Long userId, Long payslipId, ConfirmFieldsRequest request) {
        Payslip payslip = getOwnedPayslip(userId, payslipId);

        // 1) 검사: 코드가 올바른지, 중복이 없는지, 이 명세서에 있는 필드인지
        Set<PayslipFieldKey> seen = new HashSet<>();
        for (ConfirmFieldsRequest.FieldConfirmation confirmation : request.fields()) {
            PayslipFieldKey key = PayslipFieldKey.fromCode(confirmation.key())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                            "알 수 없는 필드입니다: " + confirmation.key()));
            if (!seen.add(key)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 필드가 두 번 들어 있습니다: " + confirmation.key());
            }
            if (payslip.findField(key).isEmpty()) {
                throw new BusinessException(ErrorCode.PAYSLIP_FIELD_NOT_FOUND,
                        "이 명세서에 없는 항목입니다: " + confirmation.key());
            }
        }

        // 2) 변경: 엔티티 메서드로 값과 상태를 바꾼다. 트랜잭션이 끝날 때 UPDATE가 자동으로 나간다.
        for (ConfirmFieldsRequest.FieldConfirmation confirmation : request.fields()) {
            PayslipFieldKey key = PayslipFieldKey.fromCode(confirmation.key()).orElseThrow();
            PayslipField field = payslip.findField(key).orElseThrow();
            field.confirm(confirmation.value());
        }

        return PayslipDetailResponse.from(payslip);
    }

    /**
     * 내 명세서를 필드와 함께 가져온다. 없거나 남의 것이면 404.
     * 다른 서비스(5단계 점검 서비스)도 이 메서드로 명세서를 가져온다.
     */
    public Payslip getOwnedPayslip(Long userId, Long payslipId) {
        return payslipRepository.findWithFieldsByIdAndUser_Id(payslipId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYSLIP_NOT_FOUND));
    }
}
