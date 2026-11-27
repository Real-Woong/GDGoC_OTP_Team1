package com.gdgoc.otp.infra.ocr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * otp.ocr.provider 값에 따라 OcrClient 구현이 하나만 골라지는지 확인한다.
 *
 * <p>{@link ApplicationContextRunner}: 앱 전체(DB 포함)를 띄우지 않고, 지정한 클래스만으로
 * 작은 스프링 컨테이너를 만들어 보는 테스트 도구. 설정값별로 어떤 빈이 생기는지 빠르게 확인할 수 있다.</p>
 */
class OcrClientSelectionTest {

    @Configuration
    @EnableConfigurationProperties(OcrProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, FakeOcrClient.class, HttpOcrClient.class);

    @Test
    void 설정이_없으면_가짜_OCR() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(OcrClient.class);
            assertThat(context.getBean(OcrClient.class)).isInstanceOf(FakeOcrClient.class);
        });
    }

    @Test
    void provider가_http면_OCR_서버_클라이언트() {
        runner.withPropertyValues(
                        "otp.ocr.provider=http",
                        "otp.ocr.http.base-url=http://ocr.internal:8000",
                        "otp.ocr.http.read-timeout=45s")
                .run(context -> {
                    assertThat(context).hasSingleBean(OcrClient.class);
                    assertThat(context.getBean(OcrClient.class)).isInstanceOf(HttpOcrClient.class);

                    OcrProperties.Http http = context.getBean(OcrProperties.class).http();
                    assertThat(http.baseUrl()).isEqualTo("http://ocr.internal:8000");
                    assertThat(http.readTimeout()).isEqualTo(Duration.ofSeconds(45));
                    // 지정하지 않은 값은 기본값
                    assertThat(http.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(http.hasApiKey()).isFalse();
                });
    }
}
