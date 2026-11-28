package com.gdgoc.otp.payslip.service;

import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.global.exception.ErrorCode;
import com.gdgoc.otp.infra.ocr.OcrResult;
import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.domain.PayslipWarning;
import com.gdgoc.otp.payslip.dto.ConfirmFieldsRequest;
import com.gdgoc.otp.payslip.dto.PayslipDetailResponse;
import com.gdgoc.otp.payslip.dto.PayslipSummaryResponse;
import com.gdgoc.otp.payslip.repository.PayslipRepository;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 명세서 저장·조회·필드 확인.
 *
 * <p>업로드의 앞부분(파일 검사, OCR 호출)은 {@link PayslipUploadService}가 하고,
 * 여기서는 OCR 결과를 받아 DB에 저장하는 부분({@link #saveUploaded})만 한다.</p>
 */
@Service
@Transactional(readOnly = true)
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final UserService userService;
    private final PayslipAssembler payslipAssembler;

    public PayslipService(PayslipRepository payslipRepository, UserService userService,
                          PayslipAssembler payslipAssembler) {
        this.payslipRepository = payslipRepository;
        this.userService = userService;
        this.payslipAssembler = payslipAssembler;
    }

    /**
     * OCR 결과를 명세서로 저장한다. PayslipUploadService가 OCR을 마친 뒤 호출한다.
     *
     * <p>같은 귀속월 명세서가 이미 있어도 막지 않고 경고만 남긴다.
     * (정정 명세서를 다시 받는 경우가 있기 때문이다.)</p>
     */
    @Transactional
    public PayslipDetailResponse saveUploaded(Long userId, String payPeriod, OcrResult ocrResult) {
        return save(userService.getUser(userId), payPeriod, ocrResult);
    }

    /**
     * 직접 입력용 빈 명세서를 만든다. 14개 항목이 모두 MISSING인 상태로 저장되고,
     * 사용자는 확인 화면에서 금액을 채워 PATCH /fields로 확정한다.
     *
     * <p>"빈 OCR 결과"를 저장하는 것과 같아서 업로드와 같은 조립 로직(save)을 그대로 쓴다.
     * 같은 귀속월 중복 경고도 똑같이 붙는다.</p>
     *
     * <p>약관 동의: 파일을 외부 OCR로 보내지는 않지만 급여 정보를 저장하므로 업로드와 똑같이 동의를 요구한다.</p>
     */
    @Transactional
    public PayslipDetailResponse createManual(Long userId, String payPeriod) {
        User user = userService.getUser(userId);
        if (!user.hasConsented()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }
        OcrResult empty = new OcrResult(payPeriod, null, List.of(), List.of());
        return save(user, payPeriod, empty);
    }

    private PayslipDetailResponse save(User user, String payPeriod, OcrResult ocrResult) {
        Long userId = user.getId();

        List<PayslipWarning> warnings = new ArrayList<>();
        if (payslipRepository.existsByUser_IdAndPayPeriod(userId, payPeriod)) {
            warnings.add(new PayslipWarning(null, "같은 귀속월(" + payPeriod + ") 명세서가 이미 있습니다."));
        }

        Payslip payslip = payslipAssembler.assemble(user, payPeriod, ocrResult, warnings);
        return PayslipDetailResponse.from(payslipRepository.save(payslip));
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
