package com.gdgoc.otp.rule.engine;

import com.gdgoc.otp.payslip.domain.PayslipFieldKey;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 규칙 엔진에 넘기는 "점검 재료" 묶음. 규칙은 이것만 보고 판단한다.
 *
 * <p>왜 엔티티(Payslip, User)를 직접 넘기지 않나:
 * 규칙이 DB나 JPA를 몰라도 되게 하려고. 덕분에 규칙 테스트는 스프링·DB 없이
 * {@code new CheckContext(...)}만으로 1초 안에 돌릴 수 있다.</p>
 *
 * <p>fields에는 사용자가 확인한 필드만 들어 있다. 맵에 키가 없으면 "명세서에 그 칸이 없음",
 * 키는 있는데 값이 null이면 "사용자가 '해당 없음'으로 확인함"이다.</p>
 *
 * @param payPeriod      귀속 월 "YYYY-MM"
 * @param birthYear      출생연도(모르면 null)
 * @param joinedOn       입사일(모르면 null)
 * @param isSmeEmployee  중소기업 재직 여부(모르면 null)
 * @param fields         확인된 필드 금액(원)
 * @param ruleParameters TaxRuleVersion.parameters. 값이 null이면 "아직 검증된 수치 없음"
 * @param today          오늘(한국 날짜)
 */
public record CheckContext(
        String payPeriod,
        Integer birthYear,
        LocalDate joinedOn,
        Boolean isSmeEmployee,
        Map<PayslipFieldKey, Integer> fields,
        Map<String, Object> ruleParameters,
        LocalDate today
) {
    /**
     * 컴팩트 생성자: record 생성 시 값을 검사·가공하는 곳.
     * 밖에서 넘긴 맵을 복사해 두어, 원본이 나중에 바뀌어도 이 객체는 영향받지 않게 한다.
     * (Map.copyOf는 null 값을 허용하지 않아서 HashMap으로 복사한다.)
     */
    public CheckContext {
        fields = Collections.unmodifiableMap(new HashMap<>(fields));
        ruleParameters = Collections.unmodifiableMap(new HashMap<>(ruleParameters));
    }

    /** 필드 금액. 칸이 없거나 값이 null이면 빈 Optional. */
    public Optional<Integer> amount(PayslipFieldKey key) {
        return Optional.ofNullable(fields.get(key));
    }

    /**
     * 숫자 규칙 파라미터. 없거나 null이거나 숫자가 아니면 빈 Optional.
     * JSON에서 읽은 숫자는 Integer/Long/Double 등 다양한 타입일 수 있어 Number로 받는다.
     */
    public Optional<Number> numberParameter(String name) {
        Object value = ruleParameters.get(name);
        return value instanceof Number number ? Optional.of(number) : Optional.empty();
    }

    /** 객체(중첩 JSON) 규칙 파라미터. 없거나 null이면 빈 Optional. */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> objectParameter(String name) {
        Object value = ruleParameters.get(name);
        return value instanceof Map<?, ?> map ? Optional.of((Map<String, Object>) map) : Optional.empty();
    }
}
