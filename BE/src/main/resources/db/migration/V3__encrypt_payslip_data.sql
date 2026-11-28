-- V3: 명세서 민감 정보 암호화 저장
--
-- 금액·원문 항목명·회사명·표준 외 항목·경고·점검 메시지를 앱에서 AES-256-GCM으로 암호화해 저장한다
-- (global/crypto/FieldEncryptor). 암호문은 "v1:..." 형태의 문자열이므로 해당 컬럼을 TEXT로 바꾼다.
--
-- 기존 평문 데이터: USING ...::text 로 값을 문자열로 옮겨 둔다. 앱은 "v1:"로 시작하지 않는 값을
-- 평문으로 읽고, 그 행이 다음에 저장될 때 암호화한다. (로컬 개발 DB의 데모 데이터만 해당)
--
-- 바뀌는 점: 이 컬럼들은 DB에서 값으로 검색·정렬·합계를 할 수 없고, 금액 범위 같은 CHECK 제약도 걸 수 없다.
-- 금액 검사는 앱(요청 DTO 검증, OCR 결과 조립)에서 한다.

-- 명세서 필드: 금액, 원문 항목명
ALTER TABLE payslip_field
    ALTER COLUMN value TYPE TEXT USING value::text,
    ALTER COLUMN source_label TYPE TEXT;

-- 명세서: 회사명, 표준 외 항목(JSON), 경고(JSON)
-- jsonb 기본값('[]'::jsonb)은 TEXT 컬럼에 맞지 않으므로 먼저 지운다. 앱이 항상 값을 넣으므로 기본값이 필요 없다.
ALTER TABLE payslip
    ALTER COLUMN company_name TYPE TEXT,
    ALTER COLUMN unmapped_items DROP DEFAULT,
    ALTER COLUMN unmapped_items TYPE TEXT USING unmapped_items::text,
    ALTER COLUMN warnings DROP DEFAULT,
    ALTER COLUMN warnings TYPE TEXT USING warnings::text;

-- 점검 결과 메시지는 이미 TEXT라 타입 변경이 없다(앱에서 암호화만 시작).
COMMENT ON COLUMN payslip_field.value IS '암호화(AES-256-GCM, v1:) 금액';
COMMENT ON COLUMN payslip_field.source_label IS '암호화 원문 항목명';
COMMENT ON COLUMN payslip.company_name IS '암호화 회사명';
COMMENT ON COLUMN payslip.unmapped_items IS '암호화 JSON [{label, amount}]';
COMMENT ON COLUMN payslip.warnings IS '암호화 JSON [{field, reason}]';
COMMENT ON COLUMN check_finding.message IS '암호화 안내 문구';
