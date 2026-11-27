# 협업 규칙

## 브랜치

- `main`: 항상 동작하는 상태를 유지합니다. 직접 push하지 않습니다.
- 작업 브랜치: `파트/작업내용` (영어 소문자, 하이픈)

| 파트 | 접두어 | 예시 |
| --- | --- | --- |
| 프론트 | `fe/` | `fe/confirm-screen` |
| 백엔드 | `be/` | `be/upload-api` |
| OCR | `ocr/` | `ocr/two-line-label` |
| QA | `qa/` | `qa/eval-set-v1` |
| 공통 문서 | `docs/` | `docs/backend-api` |

## 커밋 메시지

`[파트] 무엇을 했는지` 형식으로 한 줄로 씁니다.

```
[OCR] 입사일 추출 추가
[BE] 명세서 업로드 API 추가
[FE] 확인 화면 needs_review 강조
[DOCS] OCR API 에러 코드 확정
```

## Pull Request

1. 작업 브랜치에서 작업하고 GitHub에 push합니다.
2. `main`으로 PR을 엽니다. PR 템플릿의 체크리스트를 채웁니다.
3. 리뷰 1명 이상 승인 후 합칩니다.
4. 이런 경우에는 해당 팀을 꼭 리뷰어로 지정합니다.
    - 다른 팀 폴더를 고칠 때
    - `docs/`의 약속 문서(API 형식 등)를 바꿀 때
    - OCR 응답 형식(`ocr-server/ocr/schema.py`)을 바꿀 때 → 백엔드
    - 요율(`ocr-server/ocr/rates.json`)을 바꿀 때 → 계산기 담당

## 이슈

- 버그는 **버그 제보**, 새 기능이나 할 일은 **작업 요청** 템플릿으로 엽니다.
- 제목 앞에 파트를 붙입니다: `[OCR] 두 줄짜리 항목명 처리`

## 올리면 안 되는 것

- 실제 급여명세서와 그 사진·스캔 (가짜 샘플만 허용). 실제 파일은 각자 `real_samples/`에 두면 Git에서 자동 제외됩니다.
- API 키, 비밀번호, `.env` 파일
- 가상환경(`.venv/`), `node_modules/`, 빌드 결과물

## 테스트

- OCR: PR에서 `ocr-server/`가 바뀌면 GitHub Actions가 빠른 테스트를 자동으로 돌립니다. 로컬 실행 방법은 [ocr-server/README.md](ocr-server/README.md)를 참고하세요.
- 프론트·백엔드: 각 팀이 정해서 이 문서에 추가합니다.
