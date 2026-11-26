package com.gdgoc.otp.task.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * TaskTemplate.dueRule(상대 기한 규칙)을 실제 날짜로 바꾼다.
 *
 * <p>dueRule 형식은 아직 팀이 정하지 않았다. 지금은 아래 이름만 이해하고,
 * 모르는 규칙이나 "TODO"로 시작하는 규칙(미검증 더미)은 기한 없음(null)으로 둔다.
 * 잘못된 기한을 보여주는 것보다 기한을 비워 두는 편이 안전하다.</p>
 *
 * <ul>
 *   <li>END_OF_MONTH_AFTER_JOIN: 입사한 달의 다음 달 말일. 예) 2026-03-02 입사 → 2026-04-30</li>
 * </ul>
 *
 * <p>현재 V2 시드의 dueRule은 "TODO_END_OF_MONTH_AFTER_JOIN", "TODO"라서 기한이 계산되지 않는다.
 * 팀이 실제 기한을 공식 출처로 확인한 뒤 dueRule 값을 바꾸면(V3 마이그레이션) 계산이 시작된다.</p>
 */
@Component
public class DueDateCalculator {

    static final String END_OF_MONTH_AFTER_JOIN = "END_OF_MONTH_AFTER_JOIN";

    /**
     * @param dueRule  템플릿의 기한 규칙
     * @param joinedOn 사용자 입사일(모르면 null)
     * @return 기한 날짜. 계산할 수 없으면 null
     */
    public LocalDate calculate(String dueRule, LocalDate joinedOn) {
        if (dueRule == null || dueRule.startsWith("TODO")) {
            return null;
        }
        return switch (dueRule) {
            case END_OF_MONTH_AFTER_JOIN -> joinedOn == null ? null
                    : YearMonth.from(joinedOn).plusMonths(1).atEndOfMonth();
            default -> null;
        };
    }
}
