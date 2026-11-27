package com.gdgoc.otp.infra.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.yml의 {@code otp.ocr.*} 설정.
 *
 * <pre>
 * otp:
 *   ocr:
 *     provider: http                  # fake(기본) | http
 *     http:
 *       base-url: http://localhost:8000
 *       api-key: ...                  # OCR 서버의 OCR_API_KEY와 같은 값. 비우면 헤더를 보내지 않는다
 *       connect-timeout: 3s
 *       read-timeout: 30s
 * </pre>
 *
 * <p>"3s", "30s" 같은 문자열은 스프링이 {@link Duration}으로 바꿔 준다.</p>
 *
 * @param provider 사용할 OCR 구현. "fake" 또는 "http"
 * @param http     provider가 http일 때 쓰는 접속 정보
 */
@ConfigurationProperties(prefix = "otp.ocr")
public record OcrProperties(String provider, Http http) {

    public OcrProperties {
        if (http == null) {
            http = new Http(null, null, null, null);
        }
    }

    /**
     * @param baseUrl        OCR 서버 주소. 끝에 / 없이. 예: http://localhost:8000
     * @param apiKey         X-API-Key 헤더 값. 비어 있으면 보내지 않는다(OCR 서버도 키 없이 도는 로컬 개발용)
     * @param connectTimeout 연결 대기 한도. 서버가 꺼져 있으면 이 시간 뒤에 실패한다
     * @param readTimeout    응답 대기 한도. 명세서 1장에 약 9초(OCR 팀 측정)라 여유 있게 30초.
     *                       OCR 서버는 한 번에 한 장씩 처리하므로 동시 요청이 몰리면 대기 시간이 길어진다
     */
    public record Http(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {

        public Http {
            baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8000" : baseUrl;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
