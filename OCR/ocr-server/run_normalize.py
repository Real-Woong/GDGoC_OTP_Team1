"""개발용 샘플 5건으로 전체 파이프라인(prepare → preprocess → OCR → 마스킹 → 짝짓기 → 정규화)을 실행하고
정답의 표준 필드와 비교한다.

실행: .venv/bin/python run_normalize.py
결과: normalized/caseNN.json  (표준 필드, unmapped_items, warnings)
"""

import json
import shutil
from pathlib import Path

from ocr.mask import mask
from ocr.normalize import Dictionary, normalize
from ocr.pairing import pair
from ocr.prepare import prepare
from ocr.preprocess import preprocess
from ocr.recognize import get_engine, recognize

ROOT = Path(__file__).parent
SAMPLES = ROOT / "samples" / "output"
OUT = ROOT / "normalized"


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir()
    get_engine()
    d = Dictionary.load()

    total_ok = total_n = 0
    for answer_path in sorted(SAMPLES.glob("case0[1-5].json")):
        answer = json.loads(answer_path.read_text(encoding="utf-8"))
        case_id, file = answer["case_id"], answer["file"]
        doc = prepare((SAMPLES / file).read_bytes(), pdf_password=answer.get("pdf_password"))
        pre = preprocess(doc.pages[0], doc.source_format)
        paired = pair(mask(pre.image, recognize(pre.image)).boxes)
        result = normalize(paired.pairs, d)
        (OUT / f"{case_id}.json").write_text(json.dumps(
            {"case_id": case_id, **result.to_dict(),
             "unmatched_amounts": paired.to_dict()["unmatched_amounts"]},
            ensure_ascii=False, indent=2), encoding="utf-8")

        # 표준 필드 비교 (값이 없는 필드는 null이어야 맞음)
        wrong = [f"{k}: 정답 {v['value']} / 결과 {result.fields[k]['value']}"
                 for k, v in answer["fields"].items() if result.fields[k]["value"] != v["value"]]
        # unmapped 비교 (항목명·금액)
        exp_unmapped = sorted((u["source_label"], u["value"]) for u in answer["unmapped_items"])
        got_unmapped = sorted((u["source_label"], u["value"]) for u in result.unmapped_items)

        n = len(answer["fields"])
        ok = n - len(wrong)
        total_ok += ok
        total_n += n
        review = [k for k, v in result.fields.items() if v["status"] == "needs_review"]
        methods = sorted({v["match_method"] for v in result.fields.values() if v["match_method"]})
        print(f"{case_id}  표준 필드 {ok}/{n}  unmapped {'일치' if exp_unmapped == got_unmapped else '불일치'}"
              f"  매칭방법 {methods}  needs_review {review}")
        if wrong:
            print(f"    틀림: {wrong}")
        if exp_unmapped != got_unmapped:
            print(f"    unmapped 정답 {exp_unmapped} / 결과 {got_unmapped}")

    print(f"\n합계  표준 필드 {total_ok}/{total_n}")


if __name__ == "__main__":
    main()
