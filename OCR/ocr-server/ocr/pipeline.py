"""OCR 전체 파이프라인. FastAPI는 이 함수 하나만 부르면 된다.

  파일 바이트 → prepare → (페이지마다) preprocess → recognize → mask → pair
              → normalize → validate → Pydantic으로 출력 구조 확인 → dict

에러
  PrepareError  파일 문제(413·415·428 등)와 명세서가 아닌 경우(422 NOT_A_PAYSLIP).
                API에서는 e.http_status, e.to_dict()로 그대로 응답하면 된다.
  pydantic.ValidationError  출력 구조가 깨짐 = 코드 버그. API에서는 500.
"""

from __future__ import annotations

import re

from ocr.join_date import find_join_date
from ocr.mask import mask
from ocr.normalize import Dictionary, normalize
from ocr.pairing import pair
from ocr.prepare import PrepareError, prepare
from ocr.preprocess import preprocess
from ocr.recognize import TextBox, recognize
from ocr.schema import PayslipResult
from ocr.validate import validate

MIN_STANDARD_FIELDS = 3  # 표준 필드가 이보다 적게 잡히면 명세서가 아닌 것으로 본다

# 귀속 연월 찾기. 지급일(2026-04-10)을 귀속월로 착각하지 않도록 '귀속', '년 월', '분'이 붙은 것만 본다.
PERIOD_PATTERNS = [
    re.compile(r"귀속\s*(20\d{2})\s*[년.\-/]\s*(\d{1,2})"),
    re.compile(r"(20\d{2})\s*년\s*(\d{1,2})\s*월"),
    re.compile(r"(20\d{2})\s*[.\-/]\s*(\d{1,2})\s*월?\s*분"),
]


def find_pay_period(boxes: list[TextBox]) -> str | None:
    for pattern in PERIOD_PATTERNS:
        for b in boxes:
            m = pattern.search(b.text)
            if m and 1 <= int(m.group(2)) <= 12:
                return f"{m.group(1)}-{int(m.group(2)):02d}"
    return None


def make_hints(normalized, join_date: str | None, pay_period: str | None) -> list[dict]:
    """계산이나 안내에 도움이 되는 정보. 문제가 아니므로 validation이 아니라 따로 둔다."""
    hints = []
    qualifiers = [q for f in normalized.fields.values() for s in f["sources"] for q in s["qualifiers"]]
    qualifiers += [q for u in normalized.unmapped_items for q in u["qualifiers"]]
    if any("일할" in q for q in qualifiers):
        hints.append({"code": "PRORATED_PAY",
                      "message": "일할계산된 급여예요. 이 달에 입사했거나 퇴사했을 수 있어요."})
    if join_date and pay_period:
        if join_date[:7] == pay_period:
            hints.append({"code": "FIRST_MONTH",
                          "message": "입사한 달의 급여예요. 첫 달은 근무일수만큼만 받을 수 있어요."})
        if join_date[:4] == pay_period[:4]:
            hints.append({"code": "JOINED_THIS_YEAR",
                          "message": "올해 입사했어요. 첫해 연말정산은 입사 전 소득 여부에 따라 달라져요."})
    return hints


def run(data: bytes, pdf_password: str | None = None, pay_year: int | None = None,
        dictionary: Dictionary | None = None) -> dict:
    """급여명세서 파일 하나를 처리해 표준 JSON(dict)을 돌려준다.

    pay_year: 명세서에서 귀속 연도를 못 찾았을 때 요율 검사에 쓸 연도.
    """
    doc = prepare(data, pdf_password=pdf_password)

    pairs, unmatched, all_boxes = [], [], []
    for page in doc.pages:
        pre = preprocess(page, doc.source_format)
        masked = mask(pre.image, recognize(pre.image))
        paired = pair(masked.boxes)
        pairs += paired.pairs
        unmatched += paired.to_dict()["unmatched_amounts"]
        all_boxes += masked.boxes
        # 마스킹된 이미지(masked.image)는 저장하지 않는다. 필요하면 여기서 넘긴다.

    normalized = normalize(pairs, dictionary)
    pay_period = find_pay_period(all_boxes)
    join = find_join_date(all_boxes)
    year = int(pay_period[:4]) if pay_period else pay_year
    validation = validate(normalized.fields, normalized.unmapped_items, year,
                          join_date=join.value, pay_period=pay_period)

    # 표준 필드가 거의 없으면 명세서가 아닌 문서(영수증 등)로 본다
    matched = sum(f["value"] is not None for f in normalized.fields.values())
    if matched < MIN_STANDARD_FIELDS:
        raise PrepareError("NOT_A_PAYSLIP", "급여명세서로 보이지 않아요. 다시 촬영하거나 직접 입력해 주세요.")
    # 그 밖의 fail(합계를 하나도 못 읽음 등)은 확인 화면으로 넘길 수 없으므로 422로 돌려준다.
    # 그래서 정상 응답의 validation.summary는 pass 또는 warning뿐이다.
    if validation["summary"] == "fail":
        raise PrepareError("LOW_QUALITY_IMAGE", "명세서의 합계를 읽지 못했어요. 다시 촬영하거나 직접 입력해 주세요.")

    result = {
        "pay_period": pay_period,
        "join_date": join.to_dict(),
        "hints": make_hints(normalized, join.value, pay_period),
        "fields": normalized.fields,
        "unmapped_items": normalized.unmapped_items,
        "unmatched_amounts": unmatched,
        "validation": validation,
    }
    # 출력 구조 확인. 실패하면 코드 버그이므로 예외가 그대로 올라간다.
    return PayslipResult.model_validate(result).model_dump()
