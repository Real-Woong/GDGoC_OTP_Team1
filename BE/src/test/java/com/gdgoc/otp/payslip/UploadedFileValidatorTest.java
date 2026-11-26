package com.gdgoc.otp.payslip;

import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.payslip.service.UploadedFileValidator;
import com.gdgoc.otp.payslip.service.UploadedFileValidator.FileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadedFileValidatorTest {

    /** 테스트용 가짜 파일 내용: 각 형식의 시작 바이트 + 아무 데이터. */
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    static final byte[] PDF = "%PDF-1.7 dummy".getBytes();

    UploadedFileValidator validator = new UploadedFileValidator();

    @Test
    void 시작_바이트로_실제_형식을_판별한다() {
        assertThat(validator.validate(PNG)).isEqualTo(FileType.PNG);
        assertThat(validator.validate(JPEG)).isEqualTo(FileType.JPEG);
        assertThat(validator.validate(PDF)).isEqualTo(FileType.PDF);
    }

    @Test
    void 빈_파일과_허용하지_않는_형식은_거부한다() {
        assertThatThrownBy(() -> validator.validate(new byte[0])).isInstanceOf(BusinessException.class);
        // 확장자가 .png여도 내용이 텍스트면 거부(이 검사기는 내용만 본다)
        assertThatThrownBy(() -> validator.validate("hello".getBytes())).isInstanceOf(BusinessException.class);
        // 시작 바이트가 잘린 파일
        assertThatThrownBy(() -> validator.validate(new byte[]{(byte) 0x89, 0x50})).isInstanceOf(BusinessException.class);
    }
}
