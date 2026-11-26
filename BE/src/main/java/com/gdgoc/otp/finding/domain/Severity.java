package com.gdgoc.otp.finding.domain;

/**
 * 점검 결과의 중요도. 화면에서 색과 정렬 순서를 정할 때 쓴다.
 */
public enum Severity {
    /** 참고 정보. 예: "식대 비과세 한도 안에 있습니다." */
    INFO,
    /** 확인이 필요하다. 예: "중소기업 취업자 소득세 감면 대상일 수 있습니다." */
    WARNING,
    /** 기한이 임박했거나 놓치면 손해가 큰 항목. 예: "감면 신청 기한이 이번 달 말입니다." */
    CRITICAL
}
