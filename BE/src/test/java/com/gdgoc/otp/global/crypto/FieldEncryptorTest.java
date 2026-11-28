package com.gdgoc.otp.global.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FieldEncryptor 단위 테스트(DB 없음). */
class FieldEncryptorTest {

    FieldEncryptor encryptor = new FieldEncryptor(new CryptoProperties(FieldEncryptor.DEV_KEY));

    @Test
    void 암호화한_값을_복호화하면_원래_값() {
        String stored = encryptor.encrypt("2400000");

        assertThat(stored).startsWith("v1:").doesNotContain("2400000");
        assertThat(encryptor.decrypt(stored)).isEqualTo("2400000");
    }

    @Test
    void 한글도_그대로_복원된다() {
        assertThat(encryptor.decrypt(encryptor.encrypt("중식대"))).isEqualTo("중식대");
    }

    @Test
    void 같은_값도_암호문은_매번_다르다() {
        // IV(난수) 덕분에 암호문만 보고 "두 사람의 식대가 같다" 같은 사실을 알 수 없다.
        assertThat(encryptor.encrypt("200000")).isNotEqualTo(encryptor.encrypt("200000"));
    }

    @Test
    void null은_null() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void 암호문을_변조하면_복호화가_실패한다() {
        String stored = encryptor.encrypt("2400000");
        byte[] bytes = Base64.getDecoder().decode(stored.substring(3));
        bytes[bytes.length - 1] ^= 1; // 마지막 1비트만 뒤집기
        String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 다른_키로는_복호화할_수_없다() {
        String stored = encryptor.encrypt("2400000");
        String otherKey = Base64.getEncoder().encodeToString(new byte[32]);
        FieldEncryptor other = new FieldEncryptor(new CryptoProperties(otherKey));

        assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 암호화_도입_전_평문은_그대로_읽는다() {
        assertThat(encryptor.decrypt("2400000")).isEqualTo("2400000");
    }

    @Test
    void 키_길이가_틀리면_시작할_때_실패한다() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new FieldEncryptor(new CryptoProperties(shortKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    void 키가_없으면_개발용_키로_동작한다() {
        FieldEncryptor dev = new FieldEncryptor(new CryptoProperties(""));
        assertThat(encryptor.decrypt(dev.encrypt("1"))).isEqualTo("1");
    }
}
