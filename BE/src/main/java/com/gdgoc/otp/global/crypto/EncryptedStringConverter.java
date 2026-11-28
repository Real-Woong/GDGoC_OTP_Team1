package com.gdgoc.otp.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 문자열 필드를 DB에 암호화해 저장하는 JPA 컨버터.
 *
 * <p>사용법: 엔티티 필드에 {@code @Convert(converter = EncryptedStringConverter.class)}를 붙인다.
 * 그러면 Hibernate가</p>
 * <ul>
 *   <li>INSERT·UPDATE 직전에 {@link #convertToDatabaseColumn}(평문 → 암호문)을,</li>
 *   <li>SELECT 직후에 {@link #convertToEntityAttribute}(암호문 → 평문)를</li>
 * </ul>
 * 자동으로 부른다. 서비스·컨트롤러 코드는 암호화를 전혀 몰라도 된다.
 *
 * <p>{@code @Converter}(autoApply 없음): 모든 String에 자동 적용하지 않고, @Convert를 붙인 필드에만 적용한다.</p>
 *
 * <p>생성자에 FieldEncryptor를 받을 수 있는 이유: 스프링 부트가 Hibernate에게 "컨버터 객체는 스프링이 만들어 준다"고
 * 설정해 두기 때문에(SpringBeanContainer), 일반 빈처럼 생성자 주입이 된다.</p>
 *
 * <p>주의: 암호화한 컬럼은 DB에서 값으로 검색·정렬·합계를 할 수 없다(같은 값도 암호문이 매번 달라서).
 * 그런 용도의 컬럼(귀속월 등)은 암호화하지 않는다.</p>
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptor encryptor;

    public EncryptedStringConverter(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptor.decrypt(dbData);
    }
}
