package com.gdgoc.otp.rule.engine;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 등록된 모든 점검 규칙을 실행해 결과를 모은다.
 *
 * <p>스프링은 생성자 인자가 {@code List<CheckRule>}이면, CheckRule을 구현한 모든 빈을 리스트로 넣어준다.
 * 그래서 규칙을 추가해도 이 클래스는 고칠 필요가 없다.
 * 실행 순서는 각 규칙 클래스의 {@code @Order} 값(작을수록 먼저)을 따른다.</p>
 */
@Component
public class RuleEngine {

    private final List<CheckRule> rules;

    public RuleEngine(List<CheckRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<RuleResult> run(CheckContext context) {
        return rules.stream()
                .flatMap(rule -> rule.evaluate(context).stream())
                .toList();
    }
}
