"""개발용 샘플 5건(case01~05)으로 OCR 1단계(pypdf → pypdfium2)를 실행한다.

실행: .venv/bin/python run_prepare.py
결과: prepared/ 폴더에 페이지 이미지가 저장되고, 케이스별 결과가 출력된다.
"""

import json
import shutil
from pathlib import Path

from ocr.prepare import PrepareError, prepare

ROOT = Path(__file__).parent
SAMPLES = ROOT / "samples" / "output"
OUT = ROOT / "prepared"


def run(label: str, data: bytes, password: str | None, expect_error: str | None = None) -> bool:
    try:
        doc = prepare(data, pdf_password=password)
    except PrepareError as e:
        ok = e.code == expect_error
        print(f"  {label:<22} → {e.http_status} {e.code}  {'OK' if ok else 'FAIL'}")
        return ok

    if expect_error:
        print(f"  {label:<22} → 성공했지만 {expect_error}를 기대함  FAIL")
        return False
    sizes = ", ".join(f"{p.width}x{p.height}" for p in doc.pages)
    print(f"  {label:<22} → {doc.source_format}, {doc.page_count}페이지 [{sizes}], "
          f"암호={'있음' if doc.encrypted else '없음'}, 텍스트레이어={'있음' if doc.has_text_layer else '없음'}  OK")
    return doc


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir()
    results = []

    for answer_path in sorted(SAMPLES.glob("case0[1-5].json")):
        answer = json.loads(answer_path.read_text(encoding="utf-8"))
        case_id, file = answer["case_id"], answer["file"]
        data = (SAMPLES / file).read_bytes()
        password = answer.get("pdf_password")
        print(f"{case_id}  {file}")

        if password:
            # 비밀번호 PDF는 흐름도의 세 경우를 모두 확인한다
            results.append(run("비밀번호 없이", data, None, "PDF_PASSWORD_REQUIRED"))
            results.append(run("틀린 비밀번호", data, "000000", "PDF_PASSWORD_INVALID"))

        doc = run("정상 처리", data, password)
        results.append(bool(doc))
        if doc:
            for i, page in enumerate(doc.pages, 1):
                page.save(OUT / f"{case_id}_p{i}.png")

    # 지원하지 않는 형식: 확장자가 아니라 파일 내용으로 걸러지는지 확인
    print("기타")
    heic_like = b"\x00\x00\x00\x18ftypheic" + b"\x00" * 64
    results.append(run("HEIC 파일", heic_like, None, "UNSUPPORTED_FORMAT"))
    results.append(run("PDF로 위장한 텍스트", b"hello, this is not a pdf", None, "UNSUPPORTED_FORMAT"))
    results.append(run("10MB 초과", b"%PDF-" + b"0" * (11 * 1024 * 1024), None, "FILE_TOO_LARGE"))

    passed = sum(results)
    print(f"\n{passed}/{len(results)} 통과. 페이지 이미지: {OUT}")


if __name__ == "__main__":
    main()
