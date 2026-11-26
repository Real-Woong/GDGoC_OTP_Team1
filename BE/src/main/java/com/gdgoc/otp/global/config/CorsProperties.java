package com.gdgoc.otp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.yml의 {@code otp.cors.*} 값을 담는 설정 객체.
 *
 * <p>{@code @ConfigurationProperties}: yml의 값을 타입이 있는 Java 객체로 묶어 읽는다.
 * 쉼표로 구분한 문자열("a,b")도 List로 자동 변환된다.
 * {@code @Value("${...}")}를 여기저기 쓰는 것보다 설정이 한곳에 모이고 오타를 잡기 쉽다.</p>
 *
 * <p>OtpApplication의 {@code @ConfigurationPropertiesScan}이 이 클래스를 찾아 빈으로 등록한다.</p>
 *
 * @param allowedOrigins 허용할 프론트 주소 목록. 예: ["http://localhost:3000"]
 */
@ConfigurationProperties(prefix = "otp.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
