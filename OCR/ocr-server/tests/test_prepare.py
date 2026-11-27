"""1단계(파일 검증, PDF 처리) 테스트. OCR 없이 빠르게 돈다."""

from pathlib import Path

import pytest

from ocr.prepare import PrepareError, prepare

SAMPLES = Path(__file__).parent.parent / "samples" / "output"


def error_code(data, password=None):
    with pytest.raises(PrepareError) as e:
        prepare(data, pdf_password=password)
    return e.value.code, e.value.http_status


def test_digital_pdf():
    doc = prepare((SAMPLES / "case01_digital.pdf").read_bytes())
    assert doc.source_format == "pdf" and doc.page_count == 1 and doc.has_text_layer


def test_encrypted_pdf_flow():
    data = (SAMPLES / "case03_encrypted.pdf").read_bytes()
    assert error_code(data) == ("PDF_PASSWORD_REQUIRED", 428)
    assert error_code(data, "000000") == ("PDF_PASSWORD_INVALID", 428)
    assert prepare(data, pdf_password="990101").encrypted


@pytest.mark.parametrize("file,fmt", [("case02_two_column.png", "png"), ("case04_photo.jpg", "jpg")])
def test_images(file, fmt):
    doc = prepare((SAMPLES / file).read_bytes())
    assert doc.source_format == fmt and doc.page_count == 1


@pytest.mark.parametrize("name,data,expected", [
    ("HEIC", b"\x00\x00\x00\x18ftypheic" + b"\x00" * 64, ("UNSUPPORTED_FORMAT", 415)),
    ("텍스트", b"hello", ("UNSUPPORTED_FORMAT", 415)),
    ("10MB 초과", b"%PDF-" + b"0" * (11 * 1024 * 1024), ("FILE_TOO_LARGE", 413)),
    ("깨진 PDF", b"%PDF-1.7 broken", ("CORRUPTED_FILE", 400)),
], ids=lambda x: x if isinstance(x, str) else "")
def test_rejected_files(name, data, expected):
    assert error_code(data) == expected
