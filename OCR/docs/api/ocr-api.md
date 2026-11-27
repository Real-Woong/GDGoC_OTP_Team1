# OCR API 명세 (OCR 서버 ↔ 백엔드)

> 이 문서는 OCR 서버와 백엔드 사이의 **계약**입니다. 바꿀 때는 OCR 담당과 백엔드 담당이 모두 PR 리뷰에 참여합니다.
> 구현: [`ocr-server/api/main.py`](../../ocr-server/api/main.py) · 서버 실행 후 `http://localhost:8000/docs`에서도 확인 가능

OCR 서버는 급여명세서 파일 하나를 받아 세액 계산기 입력 후보를 표준 JSON으로 돌려줍니다.
결과는 사용자 확인 화면을 거친 뒤 저장·계산에 사용합니다. OCR 서버는 업로드 파일과 결과를 저장하지 않습니다.

## 요청

`POST /payslip/extract` · `multipart/form-data` · 명세서 1장에 약 9초 (개발 환경 기준)

| 이름 | 위치 | 필수 | 설명 |
| --- | --- | --- | --- |
| `file` | form | 예 | PDF, PNG, JPG · 10MB 이하 · PDF는 3페이지 이하. 형식은 확장자가 아니라 파일 내용으로 판별 |
| `pdf_password` | form | 아니오 | 비밀번호가 걸린 PDF일 때만. 428을 받은 뒤 같은 파일과 함께 다시 보냄 |
| `pay_year` | form | 아니오 | 명세서에서 귀속 연도를 못 찾았을 때 요율 검사에 쓸 연도 (예: 2026) |
| `X-API-Key` | header | 서버 설정에 따름 | OCR 서버에 `OCR_API_KEY`가 설정돼 있으면 같은 값을 보내야 함 |

```bash
curl -X POST http://<OCR 서버>:8000/payslip/extract \
  -H "X-API-Key: <내부 키>" \
  -F file=@payslip.pdf \
  -F pdf_password=990101
```

서버 상태는 `GET /health`로 확인합니다 (`model_loaded: true`면 요청 가능).

## 성공 응답 (200)

`validation.summary`는 `pass` 또는 `warning`뿐입니다. `status`가 `needs_review`인 값은 확인 화면에서 강조해 사용자 확인을 받아야 합니다.

| 키 | 타입 | 설명 |
| --- | --- | --- |
| `request_id` | string | 요청 추적용 ID (OCR 서버 로그와 같은 값) |
| `pay_period` | string \| null | 귀속월 `YYYY-MM` |
| `join_date` | object | 입사일 후보. `value`(`YYYY-MM-DD` \| null), `source_text`, `confidence`, `status`(`needs_review` \| `missing`). 찾아도 항상 사용자 확인 후 `User.joinedOn`에 저장 |
| `fields` | object | 표준 필드 14개. 명세서에 없어도 키는 항상 있음 |
| `unmapped_items` | array | 표준 필드에 맞추지 못한 항목(직책수당, 건강보험정산 등). `source_label`, `value`, `confidence`, `reason`(`not_in_dictionary` \| `blocked_word` \| `ambiguous` \| `empty`), `qualifiers`, `category`(`payment` \| `deduction` \| null) |
| `unmatched_amounts` | array | 항목명 없이 읽힌 금액. `text`, `amount`, `score` |
| `hints` | array | 안내용 정보. `code`: `PRORATED_PAY`(일할계산), `FIRST_MONTH`(입사한 달), `JOINED_THIS_YEAR`(올해 입사), `message` |
| `validation` | object | `summary`(`pass` \| `warning`), `rate_year`, `checks[]`(`code`, `level`, `field`, `expected`, `actual`, `message`) |

### `fields`의 각 항목 형식

| 키 | 타입 | 설명 |
| --- | --- | --- |
| `value` | int \| null | 원 단위 정수. 없으면 null |
| `category` | string | `payment` \| `deduction` \| `total` |
| `status` | string | `ok` \| `needs_review` \| `missing` |
| `source_label` | string \| null | 명세서에 적힌 원본 항목명 (여러 개면 `A + B`) |
| `confidence` | float \| null | OCR 신뢰도 0~1 |
| `match_method` | string \| null | `exact` \| `normalized` \| `fuzzy` |
| `sources` | array | 합산된 원본 항목 목록 |

### 표준 필드 14개

| 필드 | 구분 | 의미 |
| --- | --- | --- |
| `base_salary` | payment | 기본급 |
| `meal_allowance` | payment | 식대 (비과세 후보) |
| `car_allowance` | payment | 차량유지비 (비과세 후보) |
| `overtime_pay` | payment | 연장·야간·휴일근로수당 (합산) |
| `bonus` | payment | 상여금·성과급 |
| `payment_total` | total | 지급합계 |
| `national_pension` | deduction | 국민연금 |
| `health_insurance` | deduction | 건강보험 |
| `long_term_care` | deduction | 장기요양보험 |
| `employment_insurance` | deduction | 고용보험 |
| `income_tax` | deduction | 소득세 |
| `local_income_tax` | deduction | 지방소득세 |
| `deduction_total` | total | 공제합계 |
| `net_pay` | total | 실지급액 |

**DB 대응:** `fields`의 항목 하나가 `PayslipField` 한 줄(`key`, `value`, `confidence`, `sourceLabel`, `status`)입니다. `unmapped_items`와 `validation.checks` 중 warning은 `Payslip.unmappedItems`와 `Payslip.warnings`(json)입니다.

### 응답 예시 (일부)

```json
{
  "request_id": "3dc3bd83718c4139bd0c2aed35a4a511",
  "pay_period": "2026-03",
  "join_date": {"value": "2026-03-18", "source_text": "2026-03-18", "confidence": 0.99, "status": "needs_review"},
  "hints": [{"code": "FIRST_MONTH", "message": "입사한 달의 급여예요. 첫 달은 근무일수만큼만 받을 수 있어요."}],
  "fields": {
    "base_salary": {"value": 1354830, "category": "payment", "source_label": "기본급(일할)", "confidence": 0.99,
                    "match_method": "normalized", "status": "ok", "sources": ["..."]},
    "income_tax": {"value": 0, "category": "deduction", "source_label": "소득세", "confidence": 0.45,
                   "match_method": "exact", "status": "needs_review", "sources": ["..."]},
    "bonus": {"value": null, "category": "payment", "source_label": null, "confidence": null,
              "match_method": null, "status": "missing", "sources": []}
  },
  "unmapped_items": [{"source_label": "직책수당", "value": 100000, "confidence": 0.99,
                      "reason": "not_in_dictionary", "qualifiers": [], "category": "payment"}],
  "unmatched_amounts": [],
  "validation": {"summary": "warning", "rate_year": 2026, "checks": [
    {"code": "FIELD_NEEDS_REVIEW", "level": "warning", "field": "income_tax", "expected": null, "actual": 0,
     "message": "'소득세' 값을 확인해 주세요. (글자를 읽은 신뢰도가 낮아요)"}
  ]}
}
```

## 에러 응답

모든 에러는 같은 형식입니다: `{"request_id": "...", "error": {"code": "...", "message": "..."}}`.
`message`는 사용자에게 그대로 보여 줘도 되는 문장이고, 화면 분기는 `code`로 합니다.

| HTTP | code | 상황 | 화면 처리 (흐름도 기준) |
| --- | --- | --- | --- |
| 428 | `PDF_PASSWORD_REQUIRED` | 비밀번호가 걸린 PDF | PDF 비밀번호 입력 |
| 428 | `PDF_PASSWORD_INVALID` | 비밀번호가 틀림 | 비밀번호 다시 입력 |
| 413 | `FILE_TOO_LARGE` | 10MB 초과 | 형식·용량 오류 안내 → 파일 선택 |
| 413 | `TOO_MANY_PAGES` | PDF 3페이지 초과 | 형식·용량 오류 안내 → 파일 선택 |
| 415 | `UNSUPPORTED_FORMAT` | PDF·PNG·JPG가 아님 (HEIC 포함) | 형식·용량 오류 안내 → 파일 선택 |
| 400 | `CORRUPTED_FILE` | 파일이 깨짐 | 형식·용량 오류 안내 → 파일 선택 |
| 422 | `NOT_A_PAYSLIP` | 급여명세서로 보이지 않음 (표준 필드 3개 미만) | 다시 촬영 또는 직접 입력 |
| 422 | `LOW_QUALITY_IMAGE` | 합계를 하나도 읽지 못함 | 다시 촬영 또는 직접 입력 |
| 502 | `EXTRACTION_FAILED` | OCR 처리 중 예상 못 한 오류 | 추출 실패 → 재시도 또는 직접 입력 |
| 401 | `UNAUTHORIZED` | `X-API-Key`가 맞지 않음 | 사용자 화면 없음 (백엔드 설정 문제) |

파일 없이 호출하는 등 요청 형식이 틀리면 FastAPI 기본 422(`detail` 형식)가 나갑니다. 이 경우는 `error.code`가 없으므로 백엔드 코드 오류로 처리하면 됩니다.

## 백엔드 처리 가이드

- 응답을 받은 뒤 바로 저장하지 말고, 확인 화면에서 사용자가 확정한 값만 저장하고 계산기에 넘깁니다(`PayslipField.userConfirmed`).
- `status: needs_review` 필드, `join_date`, `unmapped_items`는 확인 화면에서 강조하거나 질문합니다. `unmapped_items`의 `reason`이 `blocked_word`이면 정산·환급 항목이라 자동 분류하지 않은 것입니다.
- 같은 파일로 재시도하려면 백엔드가 파일을 잠시 들고 있어야 합니다. OCR 서버는 파일을 저장하지 않습니다.
- 로그에는 `request_id`만 남기고 응답 본문(금액)은 남기지 않는 것을 권장합니다. 주민번호·계좌번호는 응답에 포함되지 않습니다.
- OCR 서버는 요청을 한 번에 하나씩 처리합니다. 동시에 여러 요청이 오면 줄을 서서 응답 시간이 늘어납니다.

## 함께 정할 것

- [ ] 호출 방식: 동기 응답(현재, 1장 약 9초) 유지 또는 비동기 작업 큐
- [ ] 타임아웃 값 (제안: 30초, 초과 시 502와 같은 화면)
- [ ] `OCR_API_KEY` 사용 여부와 키 전달 방법
- [ ] 에러 코드 확정: 428 외에는 임시값 (틀린 비밀번호도 428, 413·415 구분, 422 두 종류)
- [ ] 배포 방식: Docker 이미지로 전달, CPU 컨테이너로 충분(GPU 불필요)
- [ ] 입사일: 저장된 `User.joinedOn`이 있으면 확인 화면에서 다시 묻지 않을지
- [ ] 표준 필드 14개가 계산기 입력값과 맞는지 (계산기 담당 확인)
