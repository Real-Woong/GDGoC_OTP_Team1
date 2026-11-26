# OTP BE (Spring Boot)

사회초년생 급여명세서 점검 서비스의 백엔드. Spring Boot 4.2, Java 21, PostgreSQL 17.

## 처음 실행하기

1. **JDK 21**: IntelliJ → File → Project Structure → SDK → Download JDK → 21
2. **Docker Desktop** 설치 후 실행
3. DB 켜기 (이 `BE` 폴더에서):
   ```bash
   docker compose up -d
   docker compose ps        # STATUS가 healthy인지 확인
   ```
4. 앱 실행: IntelliJ에서 `OtpApplication`의 실행 버튼, 또는
   ```bash
   ./gradlew bootRun
   ```
   켜질 때 Flyway가 테이블과 데모 시드를 자동으로 만든다. `http://localhost:8080`

## 테스트

```bash
./gradlew test
```
Docker 없이 돌아간다. 테스트가 PostgreSQL 17을 임시로 띄웠다가 끈다(embedded-postgres).

## API 호출해 보기

인증은 **개발용 임시 헤더** `X-User-Subject`다. 아무 값이나 넣으면 그 이름의 사용자로 처리하고, 처음 보는 값이면 자동 가입한다. **배포 전에 실제 인증으로 반드시 교체해야 한다.**

```bash
# 1. 약관 동의 (업로드 전에 필요)
curl -X POST localhost:8080/api/users/me/terms-agreement \
  -H 'X-User-Subject: chulsoo' -H 'Content-Type: application/json' \
  -d '{"termsVersion":"2026-11-v1"}'

# 2. 명세서 업로드 (지금은 가짜 OCR이라 PNG/JPG/PDF면 무엇이든 같은 샘플 결과가 나온다)
curl -X POST localhost:8080/api/payslips -H 'X-User-Subject: chulsoo' -F 'file=@payslip.png'

# 3. 필드 확인 → 4. 점검
curl -X PATCH localhost:8080/api/payslips/1/fields -H 'X-User-Subject: chulsoo' \
  -H 'Content-Type: application/json' -d '{"fields":[{"key":"base_salary","value":2400000}, ...]}'
curl -X POST localhost:8080/api/payslips/1/check -H 'X-User-Subject: chulsoo'
```

| 메서드 | URL | 기능 |
|---|---|---|
| GET | `/api/users/me` | 내 정보 |
| PUT | `/api/users/me/profile` | 출생연도·입사일·중소기업 여부 |
| POST | `/api/users/me/terms-agreement` | 약관 동의 |
| POST | `/api/payslips` | 명세서 업로드 (multipart: `file`, 선택 `payPeriod`) |
| GET | `/api/payslips` | 내 명세서 목록 |
| GET | `/api/payslips/{id}` | 상세(확인 화면) |
| PATCH | `/api/payslips/{id}/fields` | 필드 확인·수정 |
| POST | `/api/payslips/{id}/check` | 점검 실행 |
| GET | `/api/payslips/{id}/findings` | 점검 결과 이력 |
| GET | `/api/tasks` | 내 할 일 |
| PATCH | `/api/tasks/{id}/status` | 완료 / 해당 없음 / 되돌리기 |

에러는 모두 `{ "code", "message", "fieldErrors", "timestamp" }` 형식이다. 프론트는 `code`로 분기한다.

## 환경변수

| 이름 | 기본값 | 용도 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/otp` | DB 주소 |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | DB 계정 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | 브라우저 호출을 허용할 프론트 주소 |

## 주의 (데모 단계)

- 인증: 임시 헤더. 누구나 남의 이름을 댈 수 있다.
- OCR: 가짜(`otp.ocr.provider: fake`). 실제 연동 방법은 `infra/ocr/OcrClient.java` 주석 참고.
- 세법 수치·할 일 기한: 전부 미검증 더미(DRAFT). 실제 안내에 쓰면 안 된다.
- DB 스키마 변경은 `src/main/resources/db/migration`에 **새 버전 파일**(V3__...)로 추가한다. 이미 공유된 V1, V2는 고치지 않는다.
