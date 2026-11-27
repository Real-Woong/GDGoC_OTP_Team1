"""개발용 샘플 5건으로 1단계(prepare) → 2단계(preprocess)를 실행한다.

실행: .venv/bin/python run_preprocess.py
결과:
  preprocessed/caseNN_pN.png   OCR에 넘길 전처리 결과
  preprocessed/compare/caseNN_pN.png   왼쪽 원본, 오른쪽 결과 (눈으로 확인용)
"""

import json
import shutil
from pathlib import Path

from PIL import Image

from ocr.prepare import prepare
from ocr.preprocess import preprocess

ROOT = Path(__file__).parent
SAMPLES = ROOT / "samples" / "output"
OUT = ROOT / "preprocessed"


def side_by_side(before: Image.Image, after: Image.Image, height: int = 1000) -> Image.Image:
    a = before.convert("RGB")
    b = after.convert("RGB")
    a = a.resize((int(a.width * height / a.height), height))
    b = b.resize((int(b.width * height / b.height), height))
    canvas = Image.new("RGB", (a.width + b.width + 20, height), (255, 0, 0))
    canvas.paste(a, (0, 0))
    canvas.paste(b, (a.width + 20, 0))
    return canvas


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "compare").mkdir(parents=True)

    for answer_path in sorted(SAMPLES.glob("case0[1-5].json")):
        answer = json.loads(answer_path.read_text(encoding="utf-8"))
        case_id, file = answer["case_id"], answer["file"]
        doc = prepare((SAMPLES / file).read_bytes(), pdf_password=answer.get("pdf_password"))
        print(f"{case_id}  {file}")

        for i, page in enumerate(doc.pages, 1):
            result = preprocess(page, doc.source_format)
            out = result.to_pil()
            out.save(OUT / f"{case_id}_p{i}.png")
            side_by_side(page, out).save(OUT / "compare" / f"{case_id}_p{i}.png")
            print(f"  p{i}: {page.width}x{page.height} → {out.width}x{out.height}  |  {' → '.join(result.steps)}")

    print(f"\n결과: {OUT}")


if __name__ == "__main__":
    main()
