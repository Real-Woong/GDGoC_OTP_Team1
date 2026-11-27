# 백엔드

> 백엔드 팀이 작성합니다. 아래는 지금까지 정해진 내용만 적어 둔 초안입니다.

- **기술:** Next.js API, Prisma ORM, PostgreSQL (구성도 기준)
- **역할:** 인증, 개인정보 동의, 명세서 업로드 → OCR 서버 호출, 사용자 확인 값 저장, 규칙 엔진(감면·공제 점검) 호출
- **OCR 연동:** [OCR API 명세](../docs/api/ocr-api.md)
- **DB 테이블 (7개):** User, Payslip, PayslipField, CheckFinding, TaxRuleVersion, UserTask, TaskTemplate

프론트와 같은 Next.js 앱으로 만든다면 `backend/`와 `frontend/`를 하나의 폴더(예: `web/`)로 합쳐도 됩니다. 그 경우 저장소 README의 폴더 구조도 함께 고쳐 주세요.
