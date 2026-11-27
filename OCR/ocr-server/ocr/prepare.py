"""OCR 1단계: 입력 파일 검증과 페이지 이미지 준비.

- PDF  → pypdf로 암호 확인·해제 → pypdfium2로 페이지를 이미지로 렌더링
- PNG/JPG → 형식만 확인하고 그대로 이미지로 연다
- 그 외 형식(HEIC 포함) → 지원하지 않음

이 단계 이후(전처리, OCR, 마스킹)는 여기서 다루지 않는다.
"""

from __future__ import annotations

import io
from dataclasses import dataclass, field

import pypdfium2 as pdfium
from PIL import Image, ImageOps
from pypdf import PdfReader
from pypdf.errors import PdfReadError

# PdfReader.decrypt()는 PasswordType(IntEnum)을 돌려주고, 0이면 해제 실패다.
# pypdf 7에서는 이 enum이 내부 모듈로 옮겨져서 값으로 비교한다.
NOT_DECRYPTED = 0

# ---- 설정 (백엔드와 확정되면 여기만 바꾸면 됨) ---------------------------------

MAX_FILE_BYTES = 10 * 1024 * 1024  # 10MB
MAX_PDF_PAGES = 3
RENDER_DPI = 200  # PDF를 이미지로 바꿀 때 해상도. OCR용으로 200~300 권장

# 에러 코드 → HTTP 상태. 428은 백엔드 흐름도 기준, 나머지는 임시값(확정 필요).
ERRORS = {
    "FILE_TOO_LARGE": 413,
    "UNSUPPORTED_FORMAT": 415,
    "TOO_MANY_PAGES": 413,
    "PDF_PASSWORD_REQUIRED": 428,
    "PDF_PASSWORD_INVALID": 428,
    "CORRUPTED_FILE": 400,
    "NOT_A_PAYSLIP": 422,      # 표준 필드를 거의 찾지 못함 (pipeline.py)
    "LOW_QUALITY_IMAGE": 422,  # 합계를 하나도 읽지 못함 (pipeline.py)
}

# 파일 앞부분의 시그니처로 형식을 판별한다. 확장자는 믿지 않는다.
SIGNATURES = {
    b"%PDF-": "pdf",
    b"\x89PNG\r\n\x1a\n": "png",
    b"\xff\xd8\xff": "jpg",
}


class PrepareError(Exception):
    """API에서 그대로 에러 응답으로 바꿀 수 있는 예외."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.http_status = ERRORS[code]
        self.message = message

    def to_dict(self) -> dict:
        return {"error": {"code": self.code, "message": self.message}}


@dataclass
class PreparedDocument:
    source_format: str  # "pdf" | "png" | "jpg"
    pages: list[Image.Image] = field(default_factory=list)
    encrypted: bool = False
    has_text_layer: bool = False  # 디지털 PDF면 True. 나중에 OCR을 건너뛸지 판단할 때 사용

    @property
    def page_count(self) -> int:
        return len(self.pages)


def detect_format(data: bytes) -> str:
    for sig, fmt in SIGNATURES.items():
        if data.startswith(sig):
            return fmt
    raise PrepareError("UNSUPPORTED_FORMAT", "PDF, PNG, JPG 파일만 올릴 수 있어요.")


def prepare(data: bytes, pdf_password: str | None = None) -> PreparedDocument:
    """업로드된 파일 바이트를 받아 페이지 이미지 목록을 돌려준다."""
    if len(data) > MAX_FILE_BYTES:
        raise PrepareError("FILE_TOO_LARGE", f"파일은 {MAX_FILE_BYTES // (1024 * 1024)}MB 이하만 올릴 수 있어요.")

    fmt = detect_format(data)
    if fmt == "pdf":
        return _prepare_pdf(data, pdf_password)
    return _prepare_image(data, fmt)


def _prepare_image(data: bytes, fmt: str) -> PreparedDocument:
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except Exception as e:
        raise PrepareError("CORRUPTED_FILE", "이미지 파일을 열 수 없어요.") from e
    # 휴대폰 사진은 픽셀은 옆으로 누운 채 EXIF에 회전 정보만 들어 있는 경우가 많다.
    # RGB로 바꾸면 EXIF가 사라지므로 그 전에 회전을 적용한다.
    img = ImageOps.exif_transpose(img)
    return PreparedDocument(source_format=fmt, pages=[img.convert("RGB")])


def _prepare_pdf(data: bytes, pdf_password: str | None) -> PreparedDocument:
    # 1) pypdf: 구조 확인, 암호 확인·검증, 페이지 수, 텍스트 레이어 여부
    try:
        reader = PdfReader(io.BytesIO(data))
    except PdfReadError as e:
        raise PrepareError("CORRUPTED_FILE", "PDF 파일을 열 수 없어요.") from e

    encrypted = reader.is_encrypted
    password_used: str | None = None
    if encrypted:
        # 열람 암호 없이 권한 암호만 걸린 PDF는 빈 문자열로 열린다
        if reader.decrypt("") != NOT_DECRYPTED:
            password_used = ""
        elif not pdf_password:
            raise PrepareError("PDF_PASSWORD_REQUIRED", "비밀번호가 걸린 PDF예요. 비밀번호를 입력해 주세요.")
        elif reader.decrypt(pdf_password) == NOT_DECRYPTED:
            raise PrepareError("PDF_PASSWORD_INVALID", "PDF 비밀번호가 맞지 않아요.")
        else:
            password_used = pdf_password

    page_count = len(reader.pages)
    if page_count == 0:
        raise PrepareError("CORRUPTED_FILE", "PDF에 페이지가 없어요.")
    if page_count > MAX_PDF_PAGES:
        raise PrepareError("TOO_MANY_PAGES", f"PDF는 {MAX_PDF_PAGES}페이지 이하만 올릴 수 있어요.")

    has_text = any((p.extract_text() or "").strip() for p in reader.pages)

    # 2) pypdfium2: 페이지를 이미지로 렌더링 (암호는 pypdf에서 검증한 것을 그대로 사용)
    try:
        pdf = pdfium.PdfDocument(data, password=password_used or None)
    except pdfium.PdfiumError as e:
        raise PrepareError("CORRUPTED_FILE", "PDF를 이미지로 바꾸지 못했어요.") from e

    scale = RENDER_DPI / 72
    pages = []
    try:
        for i in range(len(pdf)):
            page = pdf[i]
            pages.append(page.render(scale=scale).to_pil().convert("RGB"))
            page.close()
    finally:
        pdf.close()

    return PreparedDocument(source_format="pdf", pages=pages, encrypted=encrypted, has_text_layer=has_text)
