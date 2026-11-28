package com.gdgoc.otp.payslip.domain;

import com.gdgoc.otp.global.crypto.EncryptedJsonConverter;
import com.gdgoc.otp.global.crypto.FieldEncryptor;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/** Payslip.unmappedItems(표준 외 항목 목록)를 JSON으로 바꿔 암호화한다. 원문 항목명과 금액이 들어 있다. */
@Converter
public class EncryptedUnmappedItemsConverter extends EncryptedJsonConverter<List<UnmappedItem>> {

    public EncryptedUnmappedItemsConverter(FieldEncryptor encryptor) {
        // new TypeReference<>() {}: 제네릭 타입 정보(List<UnmappedItem>)를 실행 중에도 알 수 있게 하는 Jackson 관용구
        super(encryptor, new TypeReference<>() {
        });
    }
}
