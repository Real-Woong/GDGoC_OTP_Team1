package com.gdgoc.otp.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 정수(금액) 필드를 DB에 암호화해 저장하는 JPA 컨버터.
 *
 * <p>숫자를 문자열("2400000")로 바꾼 뒤 암호화한다. 그래서 DB 컬럼 타입은 INTEGER가 아니라 TEXT다
 * (V3__encrypt_payslip_data.sql). Java 쪽 필드 타입은 그대로 Integer라 서비스·규칙 엔진 코드는 바뀌지 않는다.</p>
 *
 * <p>null(못 읽은 값)은 null 그대로 저장한다. 0원과 "모름"을 구분해야 하기 때문이다.</p>
 */
@Converter
public class EncryptedIntegerConverter implements AttributeConverter<Integer, String> {

    private final FieldEncryptor encryptor;

    public EncryptedIntegerConverter(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(Integer attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute.toString());
    }

    @Override
    public Integer convertToEntityAttribute(String dbData) {
        String plain = encryptor.decrypt(dbData);
        return plain == null ? null : Integer.valueOf(plain);
    }
}
