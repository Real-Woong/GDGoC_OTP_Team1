package com.gdgoc.otp.global.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * DB에 저장하는 민감한 값(명세서 금액, 항목명 등)을 암호화·복호화한다.
 *
 * <p>왜 필요한가: DB 백업 파일이 유출되거나 DB 계정이 털려도, 키가 없으면 급여 금액을 읽을 수 없게 하기 위해서다.
 * 키는 DB가 아니라 서버 환경변수에만 있다. (서버와 키를 둘 다 가진 사람은 읽을 수 있다. 그건 접근 통제로 막을 문제다.)</p>
 *
 * <p>알고리즘: <b>AES-256-GCM</b>.</p>
 * <ul>
 *   <li>AES-256: 널리 쓰이는 표준 대칭키 암호. 같은 키로 암호화·복호화한다.</li>
 *   <li>GCM 모드: 암호화와 함께 "위조 검사 태그"를 붙인다. 누가 DB에서 암호문을 한 글자라도 바꾸면
 *       복호화할 때 실패한다(조용히 이상한 값이 나오지 않는다).</li>
 *   <li>IV(초기화 벡터): 암호화할 때마다 새로 뽑는 12바이트 난수. 그래서 같은 금액(예: 식대 200,000원)을
 *       여러 번 암호화해도 결과가 매번 달라, 암호문만 보고 "같은 값"인지 알 수 없다.
 *       IV는 비밀이 아니라서 암호문 앞에 붙여 함께 저장한다.</li>
 * </ul>
 *
 * <p>저장 형식: {@code "v1:" + Base64(IV 12바이트 + 암호문 + 태그 16바이트)}.
 * "v1:"은 형식 버전이다. 나중에 키를 교체하거나 알고리즘을 바꾸면 "v2:"를 추가해 둘 다 읽을 수 있게 한다.</p>
 *
 * <p>사용: 엔티티에 직접 쓰지 않는다. {@link EncryptedStringConverter} 등 JPA 컨버터가 저장·조회 순간에 자동으로 부른다.</p>
 */
@Component
public class FieldEncryptor {

    private static final Logger log = LoggerFactory.getLogger(FieldEncryptor.class);

    static final String PREFIX = "v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    /**
     * ⚠️ 개발·테스트 전용 키. 소스에 공개돼 있으므로 이 키로 암호화한 데이터는 보호되지 않는다.
     * 키 설정 없이도 로컬에서 바로 실행할 수 있게 하려는 용도다.
     */
    static final String DEV_KEY = "/2SoQYV2h5Kph8fVWpztHEz3tjqR2d0aHR6XoHDLkO4=";

    private final SecretKey key;

    /** SecureRandom: 암호학적으로 안전한 난수 생성기. IV는 예측할 수 없어야 한다. 스레드 안전하다. */
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(CryptoProperties properties) {
        String encodedKey = properties.key();
        if (encodedKey == null || encodedKey.isBlank()) {
            log.warn("OTP_ENCRYPTION_KEY가 없어 개발용 공개 키로 암호화합니다. 배포 환경에서는 반드시 설정하세요.");
            encodedKey = DEV_KEY;
        }
        this.key = parseKey(encodedKey);
    }

    /** 키 형식이 틀리면 앱이 켜질 때 바로 실패시킨다. 잘못된 키로 데이터를 쌓은 뒤에 알게 되면 복구가 어렵다. */
    private static SecretKey parseKey(String encodedKey) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("암호화 키가 Base64 형식이 아닙니다. openssl rand -base64 32 로 만드세요.");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("암호화 키는 32바이트여야 합니다(현재 %d바이트). openssl rand -base64 32 로 만드세요."
                    .formatted(bytes.length));
        }
        return new SecretKeySpec(bytes, "AES");
    }

    /** 평문 → 저장용 문자열. null은 null 그대로(값 없음도 구분해야 하므로). */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    /**
     * 저장된 문자열 → 평문.
     *
     * <p>"v1:"로 시작하지 않는 값은 암호화 도입(V3 마이그레이션) 전에 평문으로 저장된 데이터로 보고 그대로 돌려준다.
     * 이런 행도 다음에 수정·저장될 때 암호화된다.</p>
     *
     * @throws IllegalStateException 키가 다르거나 암호문이 변조된 경우. 값을 추측해 돌려주지 않고 실패시킨다.
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 원인 메시지에 암호문을 넣지 않는다.
            throw new IllegalStateException("복호화 실패: 키가 다르거나 데이터가 변조되었습니다.", e);
        }
    }
}
