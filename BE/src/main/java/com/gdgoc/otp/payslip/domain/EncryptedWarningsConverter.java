package com.gdgoc.otp.payslip.domain;

import com.gdgoc.otp.global.crypto.EncryptedJsonConverter;
import com.gdgoc.otp.global.crypto.FieldEncryptor;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/** Payslip.warnings(검증 경고 목록)를 JSON으로 바꿔 암호화한다. OCR 경고 문장에 금액이 들어갈 수 있어서 함께 암호화한다. */
@Converter
public class EncryptedWarningsConverter extends EncryptedJsonConverter<List<PayslipWarning>> {

    public EncryptedWarningsConverter(FieldEncryptor encryptor) {
        super(encryptor, new TypeReference<>() {
        });
    }
}
