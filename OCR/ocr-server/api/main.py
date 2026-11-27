"""OCR API 서버 (FastAPI).

실행: .venv/bin/uvicorn api.main:app --port 8000
문서: http://localhost:8000/docs

엔드포인트
  POST /payslip/extract   명세서 파일 → 표준 JSON
  GET  /health            서버·모델 상태

환경 변수
  OCR_API_KEY   설정하면 요청 헤더 X-API-Key가 같아야 한다(백엔드만 호출하도록).
                설정하지 않으면 인증 없이 동작한다(로컬 개발용).

원칙
  - 업로드 파일과 결과를 디스크·로그에 남기지 않는다. 로그에는 request_id, 처리 시간, 에러 코드만.
  - PaddleOCR 모델은 서버 시작 때 한 번 불러와 메모리에 둔다.
  - OCR은 CPU를 많이 쓰고 엔진이 여러 요청을 동시에 처리해도 안전한지 확인되지 않아, 한 번에 하나씩 처리한다.
"""

from __future__ import annotations

import logging
import os
import threading
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Form, Header, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from ocr import pipeline
from ocr.prepare import ERRORS, MAX_FILE_BYTES, PrepareError
from ocr.recognize import get_engine
from ocr.schema import PayslipResult

log = logging.getLogger("ocr.api")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

_ocr_lock = threading.Lock()
_model_loaded = False


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _model_loaded
    t = time.time()
    await run_in_threadpool(get_engine)  # 첫 요청이 느려지지 않도록 미리 불러 둔다
    _model_loaded = True
    log.info("OCR 모델 로딩 완료 %.1f초", time.time() - t)
    if not os.environ.get("OCR_API_KEY"):
        log.warning("OCR_API_KEY가 설정되지 않아 인증 없이 동작합니다(개발용).")
    yield


app = FastAPI(
    title="급여명세서 OCR API",
    version="0.1.0",
    description="급여명세서(PDF/PNG/JPG)에서 세액 계산기 입력 후보를 추출합니다. "
                "결과는 사용자 확인 화면을 거친 뒤 계산기에 사용하세요.",
    lifespan=lifespan,
)


class ExtractResponse(PayslipResult):
    request_id: str


def error(status: int, code: str, message: str, request_id: str) -> JSONResponse:
    return JSONResponse(status_code=status,
                        content={"request_id": request_id, "error": {"code": code, "message": message}})


ERROR_EXAMPLE = {"application/json": {"example": {
    "request_id": "8f1c…", "error": {"code": "PDF_PASSWORD_REQUIRED",
                                     "message": "비밀번호가 걸린 PDF예요. 비밀번호를 입력해 주세요."}}}}


@app.post(
    "/payslip/extract",
    response_model=ExtractResponse,
    summary="급여명세서에서 계산기 입력 후보 추출",
    responses={
        400: {"description": "CORRUPTED_FILE: 파일이 깨짐", "content": ERROR_EXAMPLE},
        401: {"description": "UNAUTHORIZED: X-API-Key가 맞지 않음", "content": ERROR_EXAMPLE},
        413: {"description": "FILE_TOO_LARGE / TOO_MANY_PAGES", "content": ERROR_EXAMPLE},
        415: {"description": "UNSUPPORTED_FORMAT: PDF·PNG·JPG가 아님", "content": ERROR_EXAMPLE},
        422: {"description": "NOT_A_PAYSLIP / LOW_QUALITY_IMAGE: 다시 촬영 또는 직접 입력 안내",
              "content": ERROR_EXAMPLE},
        428: {"description": "PDF_PASSWORD_REQUIRED / PDF_PASSWORD_INVALID: 비밀번호 입력 화면",
              "content": ERROR_EXAMPLE},
        502: {"description": "EXTRACTION_FAILED: OCR 처리 실패. 재시도 또는 직접 입력", "content": ERROR_EXAMPLE},
    },
)
async def extract(
    file: UploadFile = File(..., description="급여명세서 파일 (PDF, PNG, JPG)"),
    pdf_password: str | None = Form(None, description="비밀번호가 걸린 PDF일 때만"),
    pay_year: int | None = Form(None, description="명세서에서 귀속 연도를 못 찾았을 때 요율 검사에 쓸 연도"),
    x_api_key: str | None = Header(None),
):
    request_id = uuid.uuid4().hex
    started = time.time()

    expected_key = os.environ.get("OCR_API_KEY")
    if expected_key and x_api_key != expected_key:
        log.info("request=%s status=401", request_id)
        return error(401, "UNAUTHORIZED", "인증에 실패했어요.", request_id)

    # 용량 제한보다 1바이트 더 읽어서, 넘으면 끝까지 읽지 않고 바로 거절한다
    data = await file.read(MAX_FILE_BYTES + 1)
    try:
        result = await run_in_threadpool(_run_locked, data, pdf_password or None, pay_year)
    except PrepareError as e:
        log.info("request=%s status=%d code=%s %.1fs", request_id, e.http_status, e.code, time.time() - started)
        return error(e.http_status, e.code, e.message, request_id)
    except Exception:
        # 출력 구조 오류(코드 버그)와 예상 못 한 오류. 내용은 서버 로그에만 남기고 응답에는 넣지 않는다.
        log.exception("request=%s status=502 code=EXTRACTION_FAILED", request_id)
        return error(502, "EXTRACTION_FAILED", "명세서를 처리하지 못했어요. 다시 시도하거나 직접 입력해 주세요.",
                     request_id)

    log.info("request=%s status=200 summary=%s %.1fs", request_id, result["validation"]["summary"],
             time.time() - started)
    return {"request_id": request_id, **result}


def _run_locked(data: bytes, pdf_password: str | None, pay_year: int | None) -> dict:
    with _ocr_lock:
        return pipeline.run(data, pdf_password=pdf_password, pay_year=pay_year)


@app.get("/health", summary="서버·모델 상태")
async def health():
    return {"status": "ok" if _model_loaded else "loading", "model_loaded": _model_loaded,
            "error_codes": ERRORS}
