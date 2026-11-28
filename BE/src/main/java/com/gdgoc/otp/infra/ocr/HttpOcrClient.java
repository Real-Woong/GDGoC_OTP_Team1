package com.gdgoc.otp.infra.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 팀 OCR 서버(OCR/ocr-server, FastAPI)를 HTTP로 호출하는 OcrClient 구현.
 *
 * <p>켜는 법: application.yml에서 {@code otp.ocr.provider: http} (또는 환경변수 OCR_PROVIDER=http).
 * 이때만 이 빈이 등록되고 FakeOcrClient는 꺼진다.</p>
 *
 * <p>호출 흐름:</p>
 * <pre>
 * BE ──POST {base-url}/payslip/extract (multipart: file, pdf_password?, pay_year?)──▶ OCR 서버
 *    ◀── 200 표준 JSON  → OcrServerResponse → OcrResult 로 변환
 *    ◀── 4xx/5xx 에러 JSON → OcrException(code, 사용자용 문장)
 *    ◀── 응답 없음(타임아웃·서버 꺼짐) → OcrException(일반 실패)
 * </pre>
 *
 * <p>{@link RestClient}: 스프링이 제공하는 HTTP 호출 도구. 메서드를 이어 붙여 요청을 만든다(post → uri → body → exchange).</p>
 *
 * <p>개인정보: 파일 내용, 비밀번호, 응답 금액은 로그에 남기지 않는다. OCR 서버 로그와 대조할 수 있도록 request_id만 남긴다.</p>
 */
@Component
@ConditionalOnProperty(name = "otp.ocr.provider", havingValue = "http")
public class HttpOcrClient implements OcrClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOcrClient.class);

    static final String EXTRACT_PATH = "/payslip/extract";

    /**
     * 사용자에게 OCR 서버의 문장을 그대로 보여 줘도 되는 HTTP 상태.
     * 파일 자체의 문제(깨짐 400, 용량·페이지 413, 형식 415, 명세서 아님 422, 비밀번호 428)라서
     * "다시 찍어 주세요", "비밀번호를 입력해 주세요"처럼 사용자가 할 수 있는 일이 있다.
     * 401(키 불일치)과 5xx는 서버 설정·장애 문제라 일반 문구로 바꾼다.
     */
    private static final Set<Integer> USER_FACING_STATUSES = Set.of(400, 413, 415, 422, 428);

    private final RestClient restClient;

    /** 스프링이 쓰는 생성자. 설정값으로 주소, 타임아웃, API 키 헤더를 갖춘 RestClient를 만든다. */
    @Autowired
    public HttpOcrClient(OcrProperties properties) {
        this(buildRestClient(properties.http()));
    }

    /** 테스트용. 가짜 서버(MockRestServiceServer)에 연결된 RestClient를 넣는다. */
    HttpOcrClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(OcrProperties.Http http) {
        // 타임아웃을 정하지 않으면 OCR 서버가 멈췄을 때 사용자 요청이 끝없이 기다린다.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(http.connectTimeout());
        requestFactory.setReadTimeout(http.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(http.baseUrl())
                .requestFactory(requestFactory);
        if (http.hasApiKey()) {
            builder.defaultHeader("X-API-Key", http.apiKey());
        }
        return builder.build();
    }

    @Override
    public OcrResult extract(OcrRequest request) {
        try {
            OcrServerResponse response = restClient.post()
                    .uri(EXTRACT_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody(request))
                    // exchange: 성공·실패 응답을 모두 직접 처리한다(기본 동작은 4xx/5xx면 바로 예외).
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            throw toOcrException(res.getStatusCode(), readErrorBody(res));
                        }
                        return res.bodyTo(OcrServerResponse.class);
                    });
            if (response == null) {
                throw new OcrException("OCR 서버 응답 본문이 비어 있음");
            }
            log.info("OCR 성공 request_id={}", response.requestId());
            return toOcrResult(response);
        } catch (RestClientException e) {
            // 연결 실패, 타임아웃, 응답 JSON 형식 오류 등. 원인은 로그로만.
            throw new OcrException("OCR 서버 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * multipart/form-data 본문. 키는 폼 필드 이름, 값은 각 부분(part)의 내용이다.
     * 파일 부분은 HttpEntity로 감싸 Content-Disposition(파일 이름)과 Content-Type 헤더를 붙인다.
     * 파일 이름은 OCR 서버가 쓰지 않지만 multipart 형식상 파일 부분에 필요하다.
     */
    private static MultiValueMap<String, Object> multipartBody(OcrRequest request) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(request.contentType()));
        fileHeaders.setContentDispositionFormData("file", fileName(request.contentType()));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(request.content()), fileHeaders));
        if (request.pdfPassword() != null && !request.pdfPassword().isBlank()) {
            body.add("pdf_password", request.pdfPassword());
        }
        if (request.payYear() != null) {
            body.add("pay_year", String.valueOf(request.payYear()));
        }
        return body;
    }

    private static String fileName(String contentType) {
        return switch (contentType) {
            case MediaType.IMAGE_PNG_VALUE -> "payslip.png";
            case MediaType.IMAGE_JPEG_VALUE -> "payslip.jpg";
            default -> "payslip.pdf";
        };
    }

    /** 에러 응답 본문을 읽는다. 형식이 다르면(예: FastAPI 기본 422 {"detail": ...}) null. */
    private static OcrServerResponse.ErrorBody readErrorBody(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res) {
        try {
            return res.bodyTo(OcrServerResponse.ErrorBody.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    private static OcrException toOcrException(HttpStatusCode status, OcrServerResponse.ErrorBody body) {
        String requestId = body == null ? null : body.requestId();
        String code = (body == null || body.error() == null) ? null : body.error().code();
        String message = (body == null || body.error() == null) ? null : body.error().message();
        log.warn("OCR 실패 request_id={} status={} code={}", requestId, status.value(), code);

        // 코드는 항상 넘겨서(업로드 서비스가 에러 종류를 나누는 데 씀), 문장은 사용자에게 보여도 되는 경우만 넘긴다.
        String devMessage = "OCR 서버 %d %s".formatted(status.value(), code);
        String userMessage = USER_FACING_STATUSES.contains(status.value()) ? message : null;
        return new OcrException(devMessage, code, userMessage);
    }

    /** OCR 서버 형식 → BE 내부 형식(OcrResult). */
    static OcrResult toOcrResult(OcrServerResponse response) {
        List<OcrResult.Field> fields = new ArrayList<>();
        if (response.fields() != null) {
            for (Map.Entry<String, OcrServerResponse.FieldValue> entry : response.fields().entrySet()) {
                OcrServerResponse.FieldValue f = entry.getValue();
                if (f == null) {
                    continue;
                }
                fields.add(new OcrResult.Field(entry.getKey(), f.value(), f.confidence(), f.sourceLabel(),
                        "needs_review".equals(f.status())));
            }
        }

        List<OcrResult.Unmapped> unmapped = new ArrayList<>();
        if (response.unmappedItems() != null) {
            for (OcrServerResponse.UnmappedItem item : response.unmappedItems()) {
                unmapped.add(new OcrResult.Unmapped(item.sourceLabel(), item.value()));
            }
        }

        // 통과(pass)·건너뜀(skipped)은 버리고, 사용자에게 알릴 것(warning, fail)만 경고로 옮긴다.
        List<OcrResult.Warning> warnings = new ArrayList<>();
        if (response.validation() != null && response.validation().checks() != null) {
            for (OcrServerResponse.Check check : response.validation().checks()) {
                if (("warning".equals(check.level()) || "fail".equals(check.level())) && check.message() != null) {
                    warnings.add(new OcrResult.Warning(check.field(), check.message()));
                }
            }
        }

        // OCR 서버는 회사명을 주지 않는다.
        // validation이 있으면 OCR이 합계·실수령액 검증을 이미 했으므로 BE는 다시 검증하지 않는다.
        boolean validated = response.validation() != null;
        return new OcrResult(response.payPeriod(), null, fields, unmapped, warnings, validated);
    }
}
