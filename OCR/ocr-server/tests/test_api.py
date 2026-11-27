"""FastAPI 테스트.

빠른 테스트는 OCR 파이프라인을 가짜로 바꿔서 라우팅·인증·에러 코드만 확인한다.
느린 테스트(slow)는 실제 OCR까지 HTTP로 호출한다.
"""

import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import api.main as api_main
from ocr.prepare import PrepareError
from tests.test_validate import GOOD, make_fields
from ocr.validate import validate

SAMPLES = Path(__file__).parent.parent / "samples" / "output"


def fake_result():
    fields = make_fields(GOOD)
    return {"pay_period": "2026-03",
            "join_date": {"value": None, "source_text": None, "confidence": None, "status": "missing"},
            "hints": [], "fields": fields, "unmapped_items": [], "unmatched_amounts": [],
            "validation": validate(fields, [], 2026)}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(api_main, "get_engine", lambda: None)  # 모델 로딩 생략
    monkeypatch.delenv("OCR_API_KEY", raising=False)
    with TestClient(api_main.app) as c:
        yield c


def post(client, data=b"%PDF-fake", **form):
    return client.post("/payslip/extract", files={"file": ("payslip.pdf", data)}, data=form)


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200 and r.json()["model_loaded"] is True


def test_success_returns_result_with_request_id(client, monkeypatch):
    monkeypatch.setattr(api_main.pipeline, "run", lambda data, **kw: fake_result())
    r = post(client)
    assert r.status_code == 200
    body = r.json()
    assert len(body["request_id"]) == 32
    assert body["fields"]["base_salary"]["value"] == 2_800_000


def test_password_and_year_are_passed_through(client, monkeypatch):
    seen = {}

    def fake_run(data, pdf_password=None, pay_year=None):
        seen.update(pdf_password=pdf_password, pay_year=pay_year)
        return fake_result()

    monkeypatch.setattr(api_main.pipeline, "run", fake_run)
    post(client, pdf_password="990101", pay_year="2026")
    assert seen == {"pdf_password": "990101", "pay_year": 2026}


def test_empty_password_is_treated_as_none(client, monkeypatch):
    seen = {}
    monkeypatch.setattr(api_main.pipeline, "run",
                        lambda data, pdf_password=None, pay_year=None: seen.update(p=pdf_password) or fake_result())
    post(client, pdf_password="")
    assert seen["p"] is None


@pytest.mark.parametrize("code,status", [
    ("PDF_PASSWORD_REQUIRED", 428), ("PDF_PASSWORD_INVALID", 428), ("UNSUPPORTED_FORMAT", 415),
    ("FILE_TOO_LARGE", 413), ("NOT_A_PAYSLIP", 422), ("LOW_QUALITY_IMAGE", 422), ("CORRUPTED_FILE", 400),
])
def test_prepare_errors_become_http_errors(client, monkeypatch, code, status):
    def fail(data, **kw):
        raise PrepareError(code, "메시지")
    monkeypatch.setattr(api_main.pipeline, "run", fail)
    r = post(client)
    assert r.status_code == status
    assert r.json()["error"]["code"] == code


def test_unexpected_error_is_502_without_details(client, monkeypatch):
    def boom(data, **kw):
        raise RuntimeError("내부 정보: /secret/path")
    monkeypatch.setattr(api_main.pipeline, "run", boom)
    r = post(client)
    assert r.status_code == 502 and r.json()["error"]["code"] == "EXTRACTION_FAILED"
    assert "secret" not in r.text


def test_api_key_required_when_set(client, monkeypatch):
    monkeypatch.setenv("OCR_API_KEY", "test-key")
    monkeypatch.setattr(api_main.pipeline, "run", lambda data, **kw: fake_result())
    assert post(client).status_code == 401
    r = client.post("/payslip/extract", files={"file": ("p.pdf", b"x")}, headers={"X-API-Key": "test-key"})
    assert r.status_code == 200


def test_missing_file_is_rejected(client):
    assert client.post("/payslip/extract").status_code == 422  # FastAPI 기본 입력 검증


def test_oversized_upload_is_not_read_fully(client, monkeypatch):
    seen = {}
    monkeypatch.setattr(api_main.pipeline, "run",
                        lambda data, **kw: seen.update(n=len(data)) or fake_result())
    post(client, data=b"0" * (api_main.MAX_FILE_BYTES + 5000))
    # 제한 + 1바이트까지만 읽어서 넘긴다 (그러면 prepare가 FILE_TOO_LARGE로 거절)
    assert seen["n"] == api_main.MAX_FILE_BYTES + 1


# ---- 실제 OCR ----

@pytest.mark.slow
def test_real_ocr_over_http():
    with TestClient(api_main.app) as c:
        answer = json.loads((SAMPLES / "case03.json").read_text(encoding="utf-8"))
        data = (SAMPLES / answer["file"]).read_bytes()
        r = c.post("/payslip/extract", files={"file": (answer["file"], data)})
        assert r.status_code == 428 and r.json()["error"]["code"] == "PDF_PASSWORD_REQUIRED"
        r = c.post("/payslip/extract", files={"file": (answer["file"], data)},
                   data={"pdf_password": answer["pdf_password"]})
        assert r.status_code == 200
        body = r.json()
        assert {k: f["value"] for k, f in body["fields"].items()} == {k: f["value"] for k, f in answer["fields"].items()}
        r = c.post("/payslip/extract", files={"file": ("r.png", (SAMPLES / "case06_not_payslip.png").read_bytes())})
        assert r.status_code == 422 and r.json()["error"]["code"] == "NOT_A_PAYSLIP"
