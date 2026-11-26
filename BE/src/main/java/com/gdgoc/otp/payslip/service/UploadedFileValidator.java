package com.gdgoc.otp.payslip.service;

import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 업로드 파일 검사. 허용 형식: PNG, JPEG, PDF.
 *
 * <p>왜 Content-Type 헤더만 믿지 않나: 헤더는 브라우저(또는 공격자)가 마음대로 적어 보낼 수 있다.
 * 그래서 파일 앞부분의 "매직 넘버"(형식마다 정해진 시작 바이트)로 실제 형식을 확인한다.
 * 예: PNG는 항상 89 50 4E 47 로 시작한다.</p>
 *
 * <p>크기 제한(10MB)은 application.yml의 multipart 설정이 먼저 막는다.</p>
 */
@Component
public class UploadedFileValidator {

    /** 실제 형식 판별 결과. OCR에 넘길 contentType을 이 값으로 정한다(헤더 값 대신). */
    public enum FileType {
        PNG("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}),
        JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PDF("application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}); // "%PDF"

        private final String contentType;
        private final byte[] magic;

        FileType(String contentType, byte[] magic) {
            this.contentType = contentType;
            this.magic = magic;
        }

        public String contentType() {
            return contentType;
        }

        boolean matches(byte[] content) {
            return content.length >= magic.length
                    && Arrays.equals(Arrays.copyOf(content, magic.length), magic);
        }
    }

    /**
     * @return 실제 파일 형식
     * @throws BusinessException 비어 있거나 허용하지 않는 형식
     */
    public FileType validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "빈 파일입니다.");
        }
        return Arrays.stream(FileType.values())
                .filter(type -> type.matches(content))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE));
    }
}
