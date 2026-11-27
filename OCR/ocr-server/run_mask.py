"""개발용 샘플 5건으로 prepare → preprocess → OCR → 마스킹을 실행하고, 마스킹이 새지 않는지 확인한다.

실행: .venv/bin/python run_mask.py
결과:
  masked/caseNN.png    마스킹된 이미지
  masked/caseNN.json   마스킹된 OCR 결과(개인정보 부분은 [종류]로 바뀜) + 탐지 요약

누출 확인 방법:
  마스킹된 이미지를 다시 OCR해서, 가린 번호의 숫자 4자리 이상 조각이 하나라도 읽히면 실패로 본다.
  (가린 원래 값은 이 확인에만 메모리에서 잠깐 쓰고 저장하지 않는다. 샘플은 모두 가짜 번호다.)
"""

import json
import re
import shutil
from pathlib import Path

import numpy as np

from ocr.mask import mask
from ocr.prepare import prepare
from ocr.preprocess import preprocess
from ocr.recognize import get_engine, recognize

ROOT = Path(__file__).parent
SAMPLES = ROOT / "samples" / "output"
OUT = ROOT / "masked"
EXPECTED = {"주민등록번호": 1, "계좌번호": 1}  # 샘플마다 주민번호 1개, 계좌번호 1개가 있다


def digit_chunks(s: str, size: int = 4) -> set[str]:
    digits = re.sub(r"\D", "", s)
    return {digits[i:i + size] for i in range(len(digits) - size + 1)}


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir()
    get_engine()

    passed = 0
    cases = sorted(SAMPLES.glob("case0[1-5].json"))
    for answer_path in cases:
        answer = json.loads(answer_path.read_text(encoding="utf-8"))
        case_id, file = answer["case_id"], answer["file"]
        doc = prepare((SAMPLES / file).read_bytes(), pdf_password=answer.get("pdf_password"))
        pre = preprocess(doc.pages[0], doc.source_format)
        boxes = recognize(pre.image)

        result = mask(pre.image, boxes)
        result.image.save(OUT / f"{case_id}.png")
        (OUT / f"{case_id}.json").write_text(json.dumps({
            "case_id": case_id, "masked": result.summary(),
            "boxes": [b.to_dict() for b in result.boxes],
        }, ensure_ascii=False, indent=2), encoding="utf-8")

        # 확인 1: 기대한 개수만큼 찾았는지
        summary = result.summary()
        count_ok = summary == EXPECTED

        # 확인 2: 마스킹한 이미지를 다시 읽었을 때 가린 숫자가 보이는지
        secret_chunks = set()
        for d in result.detections:
            secret_chunks |= digit_chunks(boxes[d.box_index].text[d.start:d.end])
        # "0000", "2026"처럼 금액·날짜에도 원래 있는 조각은 누출로 셀 수 없으므로 뺀다
        public_text = " ".join(b.text for b in result.boxes)  # 개인정보를 지운 원래 OCR 결과
        secret_chunks -= digit_chunks(public_text)
        reread = " ".join(b.text for b in recognize(np.asarray(result.image)))
        leaked = sorted(c for c in secret_chunks if c in re.sub(r"\D", "", reread))
        # 숫자 조각을 그대로 보여 주지 않고 개수만 보여 준다
        leak_ok = not leaked

        # 확인 3: 개인정보가 아닌 금액은 그대로 남아 있는지
        amounts = [f["value"] for f in answer["fields"].values() if f["value"]]
        kept = sum(f"{v:,}" in reread or str(v) in reread for v in amounts)

        ok = count_ok and leak_ok
        passed += ok
        print(f"{case_id}  탐지 {summary}  |  누출 {len(leaked)}건(검사한 조각 {len(secret_chunks)}개)  |  "
              f"금액 유지 {kept}/{len(amounts)}  {'OK' if ok else 'FAIL'}")

    print(f"\n{passed}/{len(cases)} 통과. 결과: {OUT}")


if __name__ == "__main__":
    main()
