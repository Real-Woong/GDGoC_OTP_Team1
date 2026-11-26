package com.gdgoc.otp.payslip.domain;

/**
 * 명세서 필드 1칸의 추출 상태.
 */
public enum FieldStatus {
    /** 추출과 검증을 통과했다. 그래도 점검에 쓰려면 사용자가 확인(userConfirmed)해야 한다. */
    OK,
    /** 신뢰도(confidence)가 0.8 미만이거나 합계 검증에 실패했다. 화면에서 강조해 사용자에게 확인을 요청한다. */
    NEEDS_REVIEW,
    /** 명세서에서 항목을 찾지 못했다. 이때 value는 0이 아니라 null이다. */
    MISSING
}
