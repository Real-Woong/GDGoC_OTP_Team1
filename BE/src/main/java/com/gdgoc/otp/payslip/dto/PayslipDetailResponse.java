package com.gdgoc.otp.payslip.dto;

import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipWarning;
import com.gdgoc.otp.payslip.domain.UnmappedItem;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 명세서 상세 응답. "확인" 화면 전체에 필요한 데이터.
 *
 * @param fields         표준 필드 목록. PayslipFieldKey 선언 순서(지급 → 공제 → 실수령)로 정렬
 * @param unmappedItems  표준 필드로 못 맞춘 원문 항목. 화면에 "기타 항목"으로 보여준다
 * @param warnings       검증 경고
 * @param fullyConfirmed 모든 필드를 확인했는지. true일 때 "점검하기" 버튼을 활성화한다
 */
public record PayslipDetailResponse(
        Long id,
        String payPeriod,
        String companyName,
        Instant createdAt,
        List<PayslipFieldResponse> fields,
        List<UnmappedItem> unmappedItems,
        List<PayslipWarning> warnings,
        boolean fullyConfirmed
) {
    public static PayslipDetailResponse from(Payslip payslip) {
        List<PayslipFieldResponse> fields = payslip.getFields().stream()
                // DB에서 읽은 순서는 보장되지 않으므로, 화면 순서를 고정하기 위해 enum 순서로 정렬한다.
                .sorted(Comparator.comparing(field -> field.getKey().ordinal()))
                .map(PayslipFieldResponse::from)
                .toList();
        return new PayslipDetailResponse(
                payslip.getId(),
                payslip.getPayPeriod(),
                payslip.getCompanyName(),
                payslip.getCreatedAt(),
                fields,
                List.copyOf(payslip.getUnmappedItems()),
                List.copyOf(payslip.getWarnings()),
                payslip.isFullyConfirmed()
        );
    }
}
