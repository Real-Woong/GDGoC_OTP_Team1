package com.gdgoc.otp.task;

import com.gdgoc.otp.task.service.DueDateCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DueDateCalculatorTest {

    DueDateCalculator calculator = new DueDateCalculator();

    @Test
    void 입사한_달의_다음달_말일() {
        assertThat(calculator.calculate("END_OF_MONTH_AFTER_JOIN", LocalDate.of(2026, 3, 2)))
                .isEqualTo(LocalDate.of(2026, 4, 30));
        // 연말 입사 → 다음 해 1월 말일
        assertThat(calculator.calculate("END_OF_MONTH_AFTER_JOIN", LocalDate.of(2026, 12, 15)))
                .isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void 입사일을_모르거나_미검증_규칙이면_기한없음() {
        assertThat(calculator.calculate("END_OF_MONTH_AFTER_JOIN", null)).isNull();
        assertThat(calculator.calculate("TODO_END_OF_MONTH_AFTER_JOIN", LocalDate.of(2026, 3, 2))).isNull();
        assertThat(calculator.calculate("UNKNOWN_RULE", LocalDate.of(2026, 3, 2))).isNull();
    }
}
