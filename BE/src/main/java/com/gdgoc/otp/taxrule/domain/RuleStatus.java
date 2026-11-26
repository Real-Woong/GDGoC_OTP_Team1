package com.gdgoc.otp.taxrule.domain;

/**
 * 세금 규칙 버전의 검증 상태.
 */
public enum RuleStatus {
    /** 아직 공식 출처(국세청·법령)로 수치를 확인하지 않았다. 지금 시드 데이터는 전부 DRAFT다. */
    DRAFT,
    /** 공식 출처로 확인을 마쳤다. sourceUrl과 verifiedAt이 채워져 있어야 한다. */
    VERIFIED
}
