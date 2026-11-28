package com.gdgoc.otp.global.crypto;

import jakarta.persistence.AttributeConverter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 객체(주로 List)를 JSON 문자열로 바꾼 뒤 암호화해 저장하는 JPA 컨버터의 공통 부모.
 *
 * <p>예: 명세서의 표준 외 항목 [{"label":"직책수당","amount":100000}] → JSON → 암호화 → TEXT 컬럼.
 * 전에는 PostgreSQL jsonb 컬럼에 평문으로 넣었는데, 금액이 들어 있어 암호화 대상이 됐다.
 * 암호화하면 DB가 JSON 내용을 볼 수 없으므로 jsonb 대신 text 컬럼을 쓴다.</p>
 *
 * <p>왜 부모 클래스로 두나: JSON을 다시 객체로 읽으려면 "어떤 타입인지"(List&lt;UnmappedItem&gt; 등)를 알아야 한다.
 * 타입마다 이 클래스를 상속한 작은 컨버터를 하나씩 만들고, {@link TypeReference}로 타입만 알려 준다.</p>
 *
 * @param <T> 엔티티 필드 타입
 */
public abstract class EncryptedJsonConverter<T> implements AttributeConverter<T, String> {

    /** JSON 변환기(Jackson 3). 설정을 바꾸지 않으므로 하나를 만들어 공유한다. 스레드 안전하다. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final FieldEncryptor encryptor;
    private final TypeReference<T> type;

    protected EncryptedJsonConverter(FieldEncryptor encryptor, TypeReference<T> type) {
        this.encryptor = encryptor;
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        return attribute == null ? null : encryptor.encrypt(JSON.writeValueAsString(attribute));
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        String json = encryptor.decrypt(dbData);
        return json == null ? null : JSON.readValue(json, type);
    }
}
