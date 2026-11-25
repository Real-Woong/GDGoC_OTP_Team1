# GDGoC OTP Team 1

첫 월급을 받은 사회초년생이 **급여명세서를 이해하고, 입사 초기에 놓치기 쉬운 세금 절차와 공제 요건을 점검**하도록 돕는 서비스입니다.

> **현재 상태: 기획·설계 초안 (데모용)**
> 서비스 이름과 기술 스택은 아직 확정되지 않았습니다. 이 저장소의 세법 관련 값은 모두 **검증되지 않은 더미**이며, 실제 세무 조언이 아닙니다.

## 무엇을 하나요

급여명세서(PDF·사진)를 올리면 항목과 금액을 추출해 쉽게 설명하고, 놓치기 쉬운 감면·공제와 신청 기한을 점검해 "확인할 일"로 보여줍니다. 신고와 신청은 사용자가 회사나 홈택스에서 직접 합니다.

1. 급여명세서 PDF·사진에서 항목과 금액 추출 (OCR)
2. 사용자가 추출 결과를 확인·수정
3. 규칙으로 감면·공제 누락과 합계 불일치 점검
4. 기한이 있는 "확인할 일 N개" 제공

- **1차 타깃:** 입사 0~2년 차 직장인 (첫 월급, 첫 연말정산)
- **후속 확장:** N잡러(근로+부업 합산), 인플루언서(플랫폼 수입, 환율, 수입원 관리)

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/MEETING_MATERIAL/MVP/FOR_Draw_Io/user-flow.drawio`](docs/MEETING_MATERIAL/MVP/FOR_Draw_Io/user-flow.drawio) | 사용자 흐름: 명세서 업로드 → 확인 → 점검 → 할 일 (로그인 필요, 빈 결과, 요청 실패 분기 포함) |
| [`docs/DATABASE/MVP/erd.md`](docs/DATABASE/MVP/erd.md) | 데모용 데이터 구조 설명 (7개 테이블) |
| [`docs/MEETING_MATERIAL/MVP/FOR_Draw_Io/erd-mvp.drawio`](docs/MEETING_MATERIAL/MVP/FOR_Draw_Io/erd-mvp.drawio) | 위 ERD 그림 |
| [`docs/MEETING_MATERIAL/MVP/FOR_Draw_Io/architecture.drawio`](docs/MEETING_MATERIAL/MVP/FOR_Draw_Io/architecture.drawio) | 기술 구성도: 코드 확인 / 팀 제안 / 미정 / 범위 밖을 색으로 구분 |
| [`docs/MEETING_MATERIAL/MVP/SUMMARY/meeting-summary.md`](docs/MEETING_MATERIAL/MVP/SUMMARY/meeting-summary.md) | 회의용 한 장 요약과 결정 체크리스트 |
| [`docs/MEETING_MATERIAL/MVP/`](docs/MEETING_MATERIAL/MVP/) | draw.io(`FOR_Draw_Io`, Mermaid 포함)와 Eraser(`FOR_Eraser_Io`)에 붙여넣는 한글 모델 코드 |
| [`docs/ARCHIVE/`](docs/ARCHIVE/) | 축소 전 20개 테이블 설계(`FULL_20_TABLES`)와 시뮬레이션 중심 이전 흐름도(`SIMULATION_FLOW`) 보관본 |

폴더 구조는 [`docs/README.md`](docs/README.md)를 참고하세요.

`.drawio` 파일은 [app.diagrams.net](https://app.diagrams.net)에서 열 수 있습니다.

## 기술 구성

| 구분 | 내용 |
|---|---|
| **코드로 확인됨** | PostgreSQL, Prisma ORM (`prisma/schema.prisma`) |
| **팀 제안 (미확정)** | Next.js 모바일 웹/PWA, Next.js API, 외부 Vision LLM/OCR API, 규칙 기반 점검 엔진 |
| **미정** | 배포 위치, 인증 방식, 동기/비동기 처리, LLM 제공자, 알림 |

## 데이터베이스

`prisma/schema.prisma`에 7개 테이블이 정의되어 있습니다: `User`, `Payslip`, `PayslipField`, `TaxRuleVersion`, `CheckFinding`, `TaskTemplate`, `UserTask`. 급여명세서 원본 파일은 저장하지 않고, 추출된 항목과 금액만 저장하는 설계입니다.

데모 단순화로 **개인 데이터 암호화, soft delete, 동의 이력 테이블은 포함하지 않았습니다.** 실서비스로 전환할 때 다시 도입해야 합니다. 자세한 내용은 [`docs/DATABASE/MVP/erd.md`](docs/DATABASE/MVP/erd.md)를 참고하세요.

`DATABASE_URL` 환경 변수가 필요합니다. 비밀 값은 `.env`에 두고 저장소에 올리지 않습니다 (`.gitignore`로 제외).

## 주의

- **세법 값은 더미입니다.** `prisma/seed.ts`의 모든 규칙·기한은 "공식 출처로 검증 필요" 상태(DRAFT)입니다. 사용자 안내나 계산에 사용하면 안 됩니다.
- **점검 결과는 예상치 안내입니다.** 최종 확인은 회사 담당자, 국세청, 홈택스에서 해야 합니다.
- **실제 급여명세서를 저장소에 올리지 마세요.** 테스트에는 가짜 명세서를 사용합니다.

## 팀

GDGoC OTP Team 1
