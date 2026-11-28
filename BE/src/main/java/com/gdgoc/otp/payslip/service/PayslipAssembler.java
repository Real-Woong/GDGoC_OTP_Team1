package com.gdgoc.otp.payslip.service;

import com.gdgoc.otp.infra.ocr.OcrResult;
import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey.Category;
import com.gdgoc.otp.payslip.domain.PayslipWarning;
import com.gdgoc.otp.payslip.domain.UnmappedItem;
import com.gdgoc.otp.user.domain.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OCR 결과(외부 형식)를 Payslip 엔티티(내부 형식)로 조립하고, 업로드 직후 검증을 한다.
 * DB를 쓰지 않는 순수 변환이라 단위 테스트가 쉽다.
 *
 * <p>하는 일:</p>
 * <ol>
 *   <li>필드 코드 → PayslipFieldKey 변환. 모르는 코드는 unmappedItems로 옮긴다(버리지 않음).</li>
 *   <li>같은 코드가 두 번 오면 첫 번째만 쓰고 경고를 남긴다(DB unique 제약 보호).</li>
 *   <li>14개 중 OCR이 아예 안 준 필드도 MISSING 행으로 만든다. 확인 화면에 "못 읽은 칸"으로 보여주기 위해서다.</li>
 *   <li>확인 필요 판정: OCR이 판정했으면 그대로 따르고, 없으면 신뢰도 0.8 기준. OCR 경고는 그대로 옮긴다.</li>
 *   <li>OCR이 검증을 하지 않은 결과(가짜 OCR 등)만: 합계 검증에 실패하면 관련 합계 필드를 NEEDS_REVIEW로 바꾸고 경고를 남긴다
 *       (OCR 명세: needs_review = 신뢰도 0.8 미만 "또는 검증 실패").</li>
 * </ol>
 *
 * <p>합계 검증은 점검 규칙(TotalConsistencyRule, NetPayRule)과 비슷하지만 목적이 다르다.
 * 여기는 "OCR이 잘못 읽었을 수 있으니 사용자에게 확인을 요청"하는 단계이고,
 * 점검 규칙은 사용자가 확인한 값으로 "안내"를 만드는 단계다.</p>
 */
@Component
public class PayslipAssembler {

    public Payslip assemble(User user, String payPeriod, OcrResult ocr, List<PayslipWarning> extraWarnings) {
        List<UnmappedItem> unmapped = new ArrayList<>();
        List<PayslipWarning> warnings = new ArrayList<>(extraWarnings);
        // OCR이 남긴 경고(합계 불일치, 요율 이상 등)도 그대로 옮긴다.
        for (OcrResult.Warning warning : ocr.warnings()) {
            warnings.add(new PayslipWarning(warning.field(), warning.message()));
        }
        Map<PayslipFieldKey, PayslipField> fields = new EnumMap<>(PayslipFieldKey.class);

        for (OcrResult.Unmapped item : ocr.unmappedItems()) {
            unmapped.add(new UnmappedItem(item.label(), item.amount()));
        }

        // 1~2. 코드 변환과 중복 처리
        for (OcrResult.Field field : ocr.fields()) {
            Optional<PayslipFieldKey> key = PayslipFieldKey.fromCode(field.code());
            if (key.isEmpty()) {
                String label = field.sourceLabel() != null ? field.sourceLabel() : field.code();
                unmapped.add(new UnmappedItem(label, field.value()));
                continue;
            }
            if (fields.containsKey(key.get())) {
                warnings.add(new PayslipWarning(field.code(), "같은 항목이 두 번 인식되어 첫 번째 값만 사용했습니다."));
                continue;
            }
            // OCR이 확인 필요 여부를 판정했으면 그대로 따르고, 판정이 없으면(null) 신뢰도 0.8 기준으로 정한다.
            fields.put(key.get(), PayslipField.fromOcr(key.get(), field.value(), clamp(field.confidence()),
                    field.sourceLabel(), field.needsReview()));
        }

        // 3. 빠진 필드는 MISSING으로 채운다
        for (PayslipFieldKey key : PayslipFieldKey.values()) {
            fields.computeIfAbsent(key, k -> PayslipField.fromOcr(k, null, null, null));
        }

        // 4. 합계 검증. OCR이 이미 검증했으면(validated) 결과가 warnings로 들어와 있으므로 다시 하지 않는다.
        //    두 번 하면 같은 문제가 서로 다른 문장으로 두 번 보이고, 판정 기준이 달라 결과가 엇갈릴 수도 있다.
        if (!ocr.validated()) {
            checkTotal(fields, Category.PAYMENT, PayslipFieldKey.PAYMENT_TOTAL, unmapped, warnings);
            checkTotal(fields, Category.DEDUCTION, PayslipFieldKey.DEDUCTION_TOTAL, unmapped, warnings);
            checkNetPay(fields, warnings);
        }

        Payslip payslip = Payslip.create(user, payPeriod, ocr.companyName(), unmapped, warnings);
        fields.values().forEach(payslip::addField);
        return payslip;
    }

    /**
     * 항목 합이 합계보다 크면 합계를 확인 필요로 표시한다.
     * 항목 합이 더 작은 경우는 표준 외 항목(unmapped) 때문일 수 있으므로,
     * unmapped 금액까지 더해도 모자랄 때만 경고한다.
     */
    private void checkTotal(Map<PayslipFieldKey, PayslipField> fields, Category itemCategory, PayslipFieldKey totalKey,
                            List<UnmappedItem> unmapped, List<PayslipWarning> warnings) {
        Integer total = fields.get(totalKey).getValue();
        if (total == null) {
            return;
        }
        long sum = Arrays.stream(PayslipFieldKey.values())
                .filter(key -> key.getCategory() == itemCategory)
                .map(key -> fields.get(key).getValue())
                .filter(value -> value != null)
                .mapToLong(Integer::longValue)
                .sum();
        long unmappedSum = unmapped.stream()
                .map(UnmappedItem::amount)
                .filter(amount -> amount != null)
                .mapToLong(Integer::longValue)
                .sum();

        if (sum > total || sum + unmappedSum < total) {
            fields.get(totalKey).markNeedsReview();
            warnings.add(new PayslipWarning(totalKey.getCode(),
                    "항목을 더한 값과 %s가 맞지 않습니다.".formatted(totalKey.getKoreanName())));
        }
    }

    /** 지급 합계 − 공제 합계 ≠ 실수령액이면 실수령액을 확인 필요로 표시한다. */
    private void checkNetPay(Map<PayslipFieldKey, PayslipField> fields, List<PayslipWarning> warnings) {
        Integer payment = fields.get(PayslipFieldKey.PAYMENT_TOTAL).getValue();
        Integer deduction = fields.get(PayslipFieldKey.DEDUCTION_TOTAL).getValue();
        Integer netPay = fields.get(PayslipFieldKey.NET_PAY).getValue();
        if (payment == null || deduction == null || netPay == null) {
            return;
        }
        if ((long) payment - deduction != netPay) {
            fields.get(PayslipFieldKey.NET_PAY).markNeedsReview();
            warnings.add(new PayslipWarning(PayslipFieldKey.NET_PAY.getCode(),
                    "지급액 합계에서 공제액 합계를 뺀 값과 실수령액이 맞지 않습니다."));
        }
    }

    /** 외부 OCR이 0~1 범위를 벗어난 신뢰도를 주면 DB CHECK 제약에 걸리므로 범위 안으로 자른다. */
    private Double clamp(Double confidence) {
        if (confidence == null) {
            return null;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }
}
