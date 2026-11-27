"""개발용 샘플 5건으로 prepare → preprocess → OCR → 마스킹 → 짝짓기를 실행하고 정답과 비교한다.

실행: .venv/bin/python run_pairing.py
결과: paired/caseNN.json  (짝 목록, 짝 없는 금액, 정답 비교)

정답 비교:
  맞음   정답의 (항목명, 금액)과 똑같은 짝
  틀림   항목명은 맞는데 금액이 다른 짝 (가장 위험한 오류)
  누락   정답에 있는데 짝이 안 만들어진 항목
  추가   정답에 없는 항목명으로 만들어진 짝
"""

import json
import re
import shutil
from pathlib import Path

from ocr.mask import mask
from ocr.pairing import pair
from ocr.prepare import prepare
from ocr.preprocess import preprocess
from ocr.recognize import get_engine, recognize

ROOT = Path(__file__).parent
SAMPLES = ROOT / "samples" / "output"
OUT = ROOT / "paired"


def norm(s: str) -> str:
    return re.sub(r"\s+", "", s)


def compare(answer: dict, pairs: list[tuple[str, int]]) -> dict:
    expected = {norm(f["source_label"]): f["value"] for f in answer["fields"].values() if f["value"] is not None}
    expected.update({norm(u["source_label"]): u["value"] for u in answer["unmapped_items"]})
    got = {}
    for label, amount in pairs:
        got.setdefault(label, amount)
    return {
        "correct": sorted(l for l in expected if got.get(l) == expected[l]),
        "wrong": sorted(f"{l}: 정답 {expected[l]:,} / 결과 {got[l]:,}" for l in expected
                        if l in got and got[l] != expected[l]),
        "missing": sorted(l for l in expected if l not in got),
        "extra": sorted(f"{l}={a:,}" for l, a in got.items() if l not in expected),
        "total": len(expected),
    }


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir()
    get_engine()

    totals = {"correct": 0, "wrong": 0, "missing": 0, "extra": 0, "total": 0}
    for answer_path in sorted(SAMPLES.glob("case0[1-5].json")):
        answer = json.loads(answer_path.read_text(encoding="utf-8"))
        case_id, file = answer["case_id"], answer["file"]
        doc = prepare((SAMPLES / file).read_bytes(), pdf_password=answer.get("pdf_password"))
        pre = preprocess(doc.pages[0], doc.source_format)
        masked = mask(pre.image, recognize(pre.image))
        result = pair(masked.boxes)

        cmp = compare(answer, [(p.label, p.amount) for p in result.pairs])
        (OUT / f"{case_id}.json").write_text(json.dumps(
            {"case_id": case_id, **result.to_dict(), "compare": cmp}, ensure_ascii=False, indent=2), encoding="utf-8")

        for k in ("wrong", "missing", "extra"):
            totals[k] += len(cmp[k])
        totals["correct"] += len(cmp["correct"])
        totals["total"] += cmp["total"]

        print(f"{case_id}  맞음 {len(cmp['correct'])}/{cmp['total']}  틀림 {len(cmp['wrong'])}  "
              f"누락 {len(cmp['missing'])}  추가 {len(cmp['extra'])}  짝없는금액 {len(result.unmatched_amounts)}")
        for k, name in (("wrong", "틀림"), ("missing", "누락"), ("extra", "추가")):
            if cmp[k]:
                print(f"    {name}: {cmp[k]}")
        if result.unmatched_amounts:
            print(f"    짝 없는 금액: {[b.text for b in result.unmatched_amounts]}")

    print(f"\n합계  맞음 {totals['correct']}/{totals['total']}  틀림 {totals['wrong']}  "
          f"누락 {totals['missing']}  추가 {totals['extra']}")


if __name__ == "__main__":
    main()
