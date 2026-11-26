package com.gdgoc.otp.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "지금 시각"을 주는 Clock을 스프링 빈으로 등록한다.
 *
 * <p>왜 LocalDate.now()를 직접 쓰지 않나: 서비스 코드가 Clock을 주입받아 {@code LocalDate.now(clock)}을 쓰면,
 * 테스트에서 Clock을 고정된 날짜로 바꿔 끼워 "기한이 지났는지" 같은 로직을 날짜와 무관하게 검사할 수 있다.</p>
 *
 * <p>시간대를 Asia/Seoul로 두는 이유: "오늘", "이번 달 말일" 같은 날짜 계산은 사용자(한국) 기준이어야 한다.
 * 서버가 UTC로 도는 클라우드에 올라가도 날짜가 하루 어긋나지 않게 한다.
 * (DB에 저장하는 시각은 Instant라 시간대 영향이 없다.)</p>
 */
@Configuration
public class ClockConfig {

    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SERVICE_ZONE);
    }
}
