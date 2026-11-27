# OCR 서버 (급여명세서 파트)

> 프로젝트 전체 소개와 협업 구조는 [저장소 README](../README.md)를 참고하세요.
> 아래 명령은 모두 이 폴더(`ocr-server/`)에서 실행합니다.

## OCR 역할
* 급여명세서(PDF/PNG/JPG)에서 세액 계산기 입력 후보를 추출하는 API 서버. 결과는 사용자 확인 화면을 거친 뒤 저장 및 계산에 사용. (입사일/지급 항목/비과세 후보/공제내역/합계 및 실지급액/귀속월 추출 및 개인정보 마스킹 후 사용자 확인 단계로 넘기기.)

- **추출:** 지급 항목, 비과세 후보(식대·차량유지비), 공제 내역, 합계·실지급액, 귀속월, 입사일 후보
- **검증:** 합계, 요율, 인식 신뢰도. 확인이 필요한 값은 `status: needs_review`
- **개인정보:** 주민등록번호·계좌번호·전화번호·이메일은 마스킹되어 응답에 포함되지 않음. 업로드 파일은 저장하지 않음
- **외부 API 없음:** PaddleOCR을 서버 안에서 실행 (CPU로 충분, GPU 불필요)

API 요청·응답 형식과 에러 코드는 [OCR API 명세](../docs/api/ocr-api.md)를 참고해주세요.
서버를 실행한 뒤 `http://localhost:8000/docs`에서도 확인하고 직접 호출해 볼 수 있습니다.


## OCR 처리 흐름
전체 흐름은 `ocr/pipeline.py`의 `run()` 하나로 연결돼 있고, API는 이 함수만 호출함.

1. **개발용 테스트 샘플** — `samples/generate_samples.py`, `samples/output/`
    1. 가짜 급여명세서 5건(PDF, PNG, JPG)과 명세서가 아닌 영수증 1건(422 테스트용) 생성. 정답 JSON(`caseNN.json`)도 같이 생성.
    2. 모든 이름·주민번호·계좌번호는 가짜.
2. **파일 검증** — `ocr/prepare.py`
    1. 파일 내용(확장자 아님)으로 PDF·PNG·JPG만 허용. 그 외(HEIC 포함)는 415로 거절.
    2. 10MB 초과는 413, PDF 3페이지 초과도 413, 깨진 파일은 400으로 거절.
    3. 에러 코드 표는 같은 파일의 `ERRORS`에 있음.
3. **pypdf: PDF 비밀번호 확인·검증** — `ocr/prepare.py`
    1. 비밀번호 없이 오면 428 `PDF_PASSWORD_REQUIRED`, 틀리면 428 `PDF_PASSWORD_INVALID`.
    2. PDF에 글자 정보(텍스트 레이어)가 있는지도 기록.
4. **pypdfium2: PDF → 이미지 변환 (200 DPI)** — `ocr/prepare.py`
5. **Pillow: 이미지 전처리 1** — `ocr/prepare.py`
    1. EXIF 회전 보정 (PNG/JPG만). RGB로 바꾸면 EXIF 정보가 사라져서 이미지를 여는 단계에서 처리함.
6. **OpenCV: 이미지 전처리 2** — `ocr/preprocess.py`
    1. 문서 영역을 찾아 원근 보정 (PNG/JPG만. PDF는 이미 반듯함).
    2. 흑백 변환 (PaddleOCR이 회색조를 더 잘 읽음).
    3. 기울기 보정 (표의 가로선 기준, 0.2° 이상일 때. PNG/JPG만).
    4. 그림자·조명·노이즈 보정 (PNG/JPG만).
    5. 크기 맞추기: 긴 변 1,400px (PDF 포함 전부). 1,000·1,400·2,000px를 비교해 정확도와 속도로 결정.
7. **PaddleOCR: 텍스트 인식** — `ocr/recognize.py`
    1. 글자 영역 찾기(Detection): `PP-OCRv5_mobile_det` 모델. 기본 server 모델은 CPU에서 1장에 2분 이상 걸려서 교체.
    2. 글자 인식(Recognition): `korean_PP-OCRv5_mobile_rec` 모델. 신뢰도 0.3 미만인 결과는 버림.
    3. PaddleOCR 자체 전처리 기능(문서 방향, 문서 펴기, 글자 줄 방향)은 끄고 진행.
8. **개인정보 마스킹** — `ocr/mask.py`
    1. 정규식: 주민등록번호, 전화번호, 이메일 처리 (성명·주소는 마스킹하지 않음).
    2. 계좌번호: 계좌 라벨을 먼저 찾고, 그 주변 숫자 중 10~14자리인 것만 마스킹.
    3. Pillow: 탐지된 영역을 검은 박스로 마스킹.
    4. OCR 결과 글자: 해당 부분을 `은행[계좌번호]`처럼 바꿈. 원래 값은 결과 파일 어디에도 남기지 않고 종류와 개수만 남김.
9. **항목명-금액 짝짓기** — `ocr/pairing.py`
    1. 한 박스 안에 `항목명 + 금액`이 붙어 있으면 쪼개기.
    2. 중심 Y 순으로 정렬한 뒤, 행 평균과 비교해서 높이 × 0.5 이내면 같은 행으로 취급.
    3. 행 안에서 X 순으로 정렬한 뒤, 금액과 항목명을 판별. 날짜·사번·마스킹 값은 금액으로 보지 않음.
    4. 항목명 뒤 첫 금액과 짝짓기 (신뢰도는 두 박스 중 낮은 값).
    5. 짝 없는 금액은 따로 보관.
10. **항목명 정규화** — `ocr/normalize.py`, `ocr/synonyms.json`
    1. 동의어 사전: 표준 필드 14개 기준으로 정의.
    2. 매칭 순서: 사전 그대로 → 정리 후(괄호·기호·끝의 '료' 제거) → RapidFuzz.
    3. RapidFuzz: OCR 오타 정리. 2글자 이하는 쓰지 않음, 3~5글자는 1글자 차이, 6글자 이상은 2글자 차이까지. 후보가 애매하면 매칭하지 않음. RapidFuzz로 맞춘 값은 항상 `needs_review`.
    4. 원본 항목명은 덮어쓰지 않고 보관. 매칭되지 않은 항목도 버리지 않고 `unmapped_items`에 모아 둠.
    5. 정산·환급 등이 들어간 항목은 매칭을 차단하고 `unmapped_items`(이유 `blocked_word`)로 보냄. 프론트가 확인 화면에서 사용자에게 물어봄.
    6. 금액은 보지 않고 항목명만으로 판단.
11. **귀속월·입사일 추출** — `ocr/pipeline.py`, `ocr/join_date.py`
    1. 귀속월: '귀속', '년 월', '분'이 붙은 날짜에서 찾음 (지급일과 구분). 요율 연도를 정하는 데 사용.
    2. 입사일: '입사일' 라벨 주변의 날짜를 찾음. 찾아도 항상 사용자 확인(`needs_review`) 대상.
    3. 일할계산, 입사한 달, 올해 입사 여부는 `hints`로 안내.
12. **검증** — `ocr/validate.py`, `ocr/rates.json`, `ocr/schema.py`
    1. 규칙 기반 (`ocr/validate.py`): 합계 검증, 요율 검증. 검사마다 pass / warning / fail로 분류.
    2. 요율과 허용 오차는 코드가 아니라 `ocr/rates.json`에서 관리.
    3. fail: 금액이 하나도 없거나, 지급합계와 실지급액이 둘 다 없을 때. 그 외 문제는 warning이고 프론트에서 사용자 확인을 받음.
    4. API 응답 단계 (`ocr/pipeline.py`): 표준 필드가 3개 미만이면 422 `NOT_A_PAYSLIP`, 그 밖의 fail은 422 `LOW_QUALITY_IMAGE`. 그래서 정상 응답은 pass 또는 warning뿐.
    5. Pydantic (`ocr/schema.py`): 출력 JSON 구조를 검사(필드 14개, 금액 타입·범위, 지급/공제 구분). 실패하면 코드 버그로 보고 502.
13. **테스트** — `tests/`, `pytest.ini`
    1. pytest로 단계별 규칙과 검증 함수, API를 테스트. 빠른 테스트 175개는 OCR 없이 1초 이내.
    2. 실제 OCR까지 도는 통합 테스트(`-m slow`)는 샘플로 전체 흐름을 확인 (약 1분).
14. **FastAPI** — `api/main.py`, `Dockerfile`
    1. OCR을 파이썬으로 만들어서, 백엔드(Next.js)가 HTTP로 호출할 수 있게 API로 감쌈.
    2. `POST /payslip/extract`로 파일을 보내면 표준 JSON을 받음. 서버 상태는 `GET /health`, API 문서는 `/docs`.
    3. `OCR_API_KEY`를 설정하면 `X-API-Key` 헤더로 내부 인증.
15. **평가**
    1. QA에서 보내 줄 케이스로 진행할 예정.
    2. 단계별 결과를 이미지·JSON으로 확인하는 개발용 스크립트는 `run_*.py`.

## 빠른 시작

Python 3.12 기준입니다.

```bash
cd ocr-server
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn api.main:app --port 8000
```

처음 실행할 때 OCR 모델(수십 MB)을 `~/.paddlex/official_models`에 내려받습니다. 이후에는 다시 받지 않습니다.
서버 시작 후 `GET /health`가 `"model_loaded": true`를 돌려주면 요청을 받을 수 있습니다.

```bash
# 샘플로 시험 호출 (비밀번호 PDF)
curl -F file=@samples/output/case03_encrypted.pdf -F pdf_password=990101 http://localhost:8000/payslip/extract
```

### Docker

```bash
docker build -t payslip-ocr .
docker run -p 8000:8000 -e OCR_API_KEY=<내부 키> payslip-ocr
```

> Dockerfile은 아직 빌드 검증 전입니다!! 빌드 중 오류가 나면 알려 주세요!

## 환경 변수

| 이름 | 설명 |
|---|---|
| `OCR_API_KEY` | 설정하면 요청 헤더 `X-API-Key`가 같아야 호출 가능. 설정하지 않으면 인증 없이 동작(로컬 개발용) |

## 성능

- 명세서 1장에 약 9초 (개발용 Mac CPU 기준). 서버 사양에 따라 다릅니다.
- 요청은 한 번에 하나씩 처리합니다. 동시에 여러 요청이 오면 순서대로 기다립니다.

## 폴더 구조 (`ocr-server/` 안)

```
api/main.py          FastAPI 서버 (POST /payslip/extract, GET /health)
ocr/
  pipeline.py        전체 흐름. API는 run() 하나만 호출
  prepare.py         1. 파일 검증, PDF 비밀번호 확인, PDF → 이미지 (에러 코드 표 ERRORS)
  preprocess.py      2. 원근·기울기·그림자 보정, 크기 조정
  recognize.py       3. PaddleOCR 글자 인식
  mask.py            4. 개인정보 마스킹
  pairing.py         5. 항목명-금액 짝짓기 (좌표 기반)
  normalize.py       6. 항목명 → 표준 필드 (동의어 사전 + RapidFuzz)
  validate.py        7. 합계·요율 검증
  join_date.py       입사일 추출
  schema.py          출력 JSON 구조 (Pydantic)
  synonyms.json      동의어 사전 (항목명 추가는 여기서)
  rates.json         연도별 요율·허용 오차 (현재 2026년은 개발용 가정값)
tests/               pytest 테스트
samples/             가짜 급여명세서 생성기와 샘플 6건 (모든 개인정보는 가짜)
run_*.py             단계별 결과를 이미지·JSON으로 확인하는 개발용 스크립트
```

## 테스트

```bash
.venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest            # 빠른 테스트 (OCR 없이, 1초 이내)
.venv/bin/python -m pytest -m slow    # 실제 OCR까지 도는 통합 테스트 (약 1분)
```

## 현재까지 한계

- 개발용 가짜 샘플 5건으로만 확인했습니다. 실제 명세서 정확도는 QA 평가 후 공유 예정입니다.
- `rates.json`의 2026년 요율은 확정값이 아닙니다. 계산기 팀 값으로 교체가 필요합니다.
- 에러 코드는 428을 제외하고 임시값입니다. 백엔드와 확정이 필요합니다.
- 두 줄짜리 항목명, 항목명 아래에 금액이 적힌 양식은 아직 처리하지 않습니다.
