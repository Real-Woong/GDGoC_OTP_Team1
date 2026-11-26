package com.gdgoc.otp.rule.rules;

/**
 * 금액을 "1,234,000원" 형식 문자열로 바꾸는 도우미. 규칙 안내 문구에서 쓴다.
 */
final class Won {

    private Won() {
    }

    static String format(long amount) {
        return String.format("%,d원", amount);
    }
}
