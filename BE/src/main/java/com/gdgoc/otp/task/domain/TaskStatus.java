package com.gdgoc.otp.task.domain;

/**
 * 사용자 할 일의 진행 상태.
 */
public enum TaskStatus {
    /** 아직 하지 않았다. 새로 만든 할 일의 기본값. */
    TODO,
    /** 사용자가 완료했다고 표시했다. */
    DONE,
    /** 사용자에게 해당하지 않는다. 예: 중소기업이 아니라 감면 신청이 필요 없음. */
    NOT_APPLICABLE
}
