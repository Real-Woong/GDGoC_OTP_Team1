"""개발용 샘플 5건으로 1단계(prepare) → 2단계(preprocess) → 3단계(PaddleOCR)를 실행한다.

전처리를 하지 않은 원본 페이지도 같이 OCR해서, 전처리 효과를 비교한다.

실행: .venv/bin/python run_ocr.py
결과:
  ocr_results/caseNN.json      전처리 후 OCR 결과(글자, 신뢰도, 박스 좌표)
  ocr_results/vis/caseNN.png   인식된 박스를 이미지 위에 그린 것

정확도 확인 방법(간이):
  정답 JSON의 항목명(source_label)과 금액(value)이 OCR 글자에 그대로 나왔는지 센다.
  항목명-금액 짝짓기는 다음 단계라서 여기서는 '읽어 냈는지'만 본다.
"""

import json
import re
import shutil
import time
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

from ocr.prepare import prepare
from ocr.preprocess import preprocess
from ocr.recognize import TextBox, get_engine, recognize

ROOT = Path(__file__).parent
SAMPLES = ROOT / "samples" / "output"
OUT = ROOT / "ocr_results"


def norm(s: str) -> str:
    return re.sub(r"\s+", "", s)


def found_label(label: str, texts: list[str]) -> bool:
    return any(norm(label) in t for t in texts)


def found_value(value: int, texts: list[str]) -> bool:
    # 1,354,830 이 13,548,300 의 일부로 잘못 잡히지 않도록 앞뒤가 숫자·쉼표가 아니어야 한다
    pattern = r"(?<![\d,])" + re.escape(f"{value:,}") + r"(?![\d,])"
    return any(re.search(pattern, t) for t in texts)


def score(answer: dict, boxes: list[TextBox]) -> dict:
    texts = [norm(b.text) for b in boxes]
    items = [(f["source_label"], f["value"]) for f in answer["fields"].values() if f["value"] is not None]
    items += [(u["source_label"], u["value"]) for u in answer["unmapped_items"]]
    missing_labels = [l for l, _ in items if not found_label(l, texts)]
    missing_values = [f"{l}={v:,}" for l, v in items if not found_value(v, texts)]
    return {
        "total": len(items),
        "labels_found": len(items) - len(missing_labels),
        "values_found": len(items) - len(missing_values),
        "missing_labels": missing_labels,
        "missing_values": missing_values,
    }


def draw_boxes(gray: np.ndarray, boxes: list[TextBox]) -> Image.Image:
    img = Image.fromarray(cv2.cvtColor(gray, cv2.COLOR_GRAY2RGB))
    draw = ImageDraw.Draw(img)
    for b in boxes:
        color = (0, 160, 0) if b.score >= 0.9 else (230, 120, 0)  # 신뢰도 0.9 미만은 주황
        draw.rectangle((b.x0, b.y0, b.x1, b.y1), outline=color, width=3)
    return img


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "vis").mkdir(parents=True)

    print("PaddleOCR 모델 로딩 중... (처음 한 번, 수십 초)")
    t = time.time()
    get_engine()
    print(f"로딩 {time.time() - t:.1f}초\n")

    summary = []
    for answer_path in sorted(SAMPLES.glob("case0[1-5].json")):
        answer = json.loads(answer_path.read_text(encoding="utf-8"))
        case_id, file = answer["case_id"], answer["file"]
        doc = prepare((SAMPLES / file).read_bytes(), pdf_password=answer.get("pdf_password"))
        page = doc.pages[0]  # 샘플은 모두 1페이지

        # 전처리 없이
        t = time.time()
        raw_boxes = recognize(cv2.cvtColor(np.asarray(page), cv2.COLOR_RGB2BGR))
        raw_sec = time.time() - t

        # 전처리 후
        pre = preprocess(page, doc.source_format)
        t = time.time()
        boxes = recognize(pre.image)
        pre_sec = time.time() - t

        raw_score, pre_score = score(answer, raw_boxes), score(answer, boxes)
        (OUT / f"{case_id}.json").write_text(json.dumps({
            "case_id": case_id, "file": file, "preprocess_steps": pre.steps,
            "seconds": round(pre_sec, 1), "score": pre_score,
            "boxes": [b.to_dict() for b in boxes],
        }, ensure_ascii=False, indent=2), encoding="utf-8")
        draw_boxes(pre.image, boxes).save(OUT / "vis" / f"{case_id}.png")

        n = pre_score["total"]
        print(f"{case_id}  {file}")
        for name, s, sec in (("전처리 없이", raw_score, raw_sec), ("전처리 후", pre_score, pre_sec)):
            print(f"  {name:<6} 항목명 {s['labels_found']}/{n}  금액 {s['values_found']}/{n}  ({sec:.1f}초)")
        if pre_score["missing_labels"] or pre_score["missing_values"]:
            print(f"    전처리 후 못 찾은 항목명: {pre_score['missing_labels']}")
            print(f"    전처리 후 못 찾은 금액:   {pre_score['missing_values']}")
            # 비슷한 글자로 잘못 읽었는지 보기 위해 숫자가 들어간 인식 결과를 같이 보여 준다
        summary.append((raw_score, pre_score))

    def total(i, key):
        return sum(s[i][key] for s in summary)

    n = sum(s[1]["total"] for s in summary)
    print(f"\n합계  전처리 없이: 항목명 {total(0, 'labels_found')}/{n}, 금액 {total(0, 'values_found')}/{n}"
          f"  |  전처리 후: 항목명 {total(1, 'labels_found')}/{n}, 금액 {total(1, 'values_found')}/{n}")
    print(f"결과: {OUT}")


if __name__ == "__main__":
    main()
