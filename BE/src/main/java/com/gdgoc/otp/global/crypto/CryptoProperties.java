package com.gdgoc.otp.global.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 {@code otp.crypto.*} 설정.
 *
 * @param key 데이터 암호화 키. 32바이트(256비트) 난수를 Base64로 인코딩한 문자열.
 *            만드는 법: {@code openssl rand -base64 32}
 *            비어 있으면 {@link FieldEncryptor}가 개발용 고정 키를 쓰고 경고 로그를 남긴다.
 *            ⚠️ 배포 환경에서는 반드시 환경변수 OTP_ENCRYPTION_KEY로 넣고, 깃에 올리지 않는다.
 *            키를 잃어버리면 이미 저장된 데이터를 영영 복호화할 수 없다.
 */
@ConfigurationProperties(prefix = "otp.crypto")
public record CryptoProperties(String key) {
}
