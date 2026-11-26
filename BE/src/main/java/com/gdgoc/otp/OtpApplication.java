package com.gdgoc.otp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 앱 시작점. IntelliJ에서 main 옆의 실행 버튼을 누르거나 ./gradlew bootRun 으로 실행한다.
 * 실행 전에 PostgreSQL이 떠 있어야 한다(docker compose up -d).
 *
 * <p>{@code @ConfigurationPropertiesScan}: @ConfigurationProperties가 붙은 설정 클래스(CorsProperties 등)를 찾아 등록한다.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OtpApplication {

	public static void main(String[] args) {
		SpringApplication.run(OtpApplication.class, args);
	}

}
