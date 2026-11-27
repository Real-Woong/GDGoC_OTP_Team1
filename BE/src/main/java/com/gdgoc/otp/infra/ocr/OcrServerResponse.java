package com.gdgoc.otp.infra.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * OCR 서버(OCR/ocr-server) 응답 JSON을 그대로 옮긴 형식. {@link HttpOcrClient} 안에서만 쓴다.
 *
 * <p>계약 문서: OCR/docs/api/ocr-api.md, 원본 정의: OCR/ocr-server/ocr/schema.py</p>
 *
 * <p>왜 OcrResult로 바로 받지 않나: OCR 서버는 snake_case 키를 쓰고 fields가 {"base_salary": {...}} 같은
 * 객체(Map)다. BE 내부 형식(OcrResult)과 모양이 다르다. 외부 형식을 이 클래스에 가둬 두면
 * OCR 서버 응답이 바뀌어도 이 파일과 HttpOcrClient의 변환 코드만 고치면 된다.</p>
 *
 * <p>{@code @JsonProperty("pay_period")}: JSON 키 이름과 Java 이름이 다를 때 연결한다.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}: 우리가 안 쓰는 키(hints, unmatched_amounts 등)나
 * OCR 서버가 나중에 추가하는 키가 있어도 에러 없이 무시한다.</p>
 *
 * <p>지금 BE가 쓰지 않는 정보: join_date(입사일 후보), hints(첫 달 일할계산 안내 등), unmatched_amounts.
 * 확인 화면에서 쓰기로 정하면 여기에 추가한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OcrServerResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("pay_period") String payPeriod,
        Map<String, FieldValue> fields,
        @JsonProperty("unmapped_items") List<UnmappedItem> unmappedItems,
        Validation validation
) {

    /** 표준 필드 하나. status: ok | needs_review | missing */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FieldValue(
            Integer value,
            String status,
            @JsonProperty("source_label") String sourceLabel,
            Double confidence
    ) {
    }

    /** 표준 필드로 못 맞춘 항목. value는 정산·환급이면 음수일 수 있다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnmappedItem(
            @JsonProperty("source_label") String sourceLabel,
            Integer value
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Validation(List<Check> checks) {
    }

    /** 검증 항목 하나. level: pass | warning | fail | skipped */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Check(String code, String level, String field, String message) {
    }

    /** 에러 응답: {"request_id": "...", "error": {"code": "...", "message": "..."}} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(@JsonProperty("request_id") String requestId, Error error) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Error(String code, String message) {
        }
    }
}
