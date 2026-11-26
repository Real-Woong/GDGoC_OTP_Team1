package com.gdgoc.otp;

import com.gdgoc.otp.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

/**
 * 앱 전체가 뜨는지 확인하는 가장 기본 테스트.
 * 이 테스트가 통과하면 Flyway 마이그레이션이 성공했고,
 * JPA 엔티티와 테이블 구조가 일치한다(ddl-auto: validate)는 뜻이다.
 */
class OtpApplicationTests extends IntegrationTestSupport {

    @Test
    void contextLoads() {
    }
}
