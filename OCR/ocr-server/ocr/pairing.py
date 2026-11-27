"""OCR 5단계: 항목명-금액 짝짓기 (좌표 기반).

마스킹된 OCR 결과(TextBox 목록)를 받아 (항목명, 금액) 쌍을 만든다.
항목명을 표준 필드(base_salary 등)로 바꾸는 일은 다음 단계에서 한다.

방식 (열 탐지 없이 단순하게)
  1. 한 박스에 "기본급 2,800,000"처럼 항목명과 금액이 붙어 있으면 둘로 쪼갠다
  2. 중심 Y 순으로 정렬하고, 현재 행의 평균 중심 Y와의 차이가
     (박스 높이 × ROW_TOLERANCE) 이내면 같은 행으로 묶는다
  3. 행 안에서 X 순으로 정렬하고, 각 박스를 금액 / 항목명으로 나눈다
  4. 항목명 뒤에 처음 나오는 금액을 짝으로 묶는다
     → 1단 양식(항목|금액)과 2단 양식(항목|금액|항목|금액)이 같은 규칙으로 처리된다
  5. 짝이 없는 금액은 버리지 않고 따로 모은다

나중에 두 줄짜리 항목명, 위아래 배치 양식 등이 나오면 열 탐지를 추가한다.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field, replace
from statistics import mean

from ocr.recognize import TextBox

# ---- 설정 -----------------------------------------------------------------

ROW_TOLERANCE = 0.5  # 같은 행으로 보는 중심 Y 차이 = 박스 높이 × 이 값

# ---- 금액·항목명 판별 -------------------------------------------------------

# 금액: "2,800,000" / "980" / "0" / "131,.630"(OCR 잡음) / "-12,000" / "△12,000"(마이너스 표기) / 끝에 "원"
# 쉼표 없는 4자리 이상 숫자(사번 20250312 등)나 하이픈이 들어간 값(날짜, 번호)은 금액이 아니다.
AMOUNT_RE = re.compile(r"^(?P<sign>[-△▲])?(?P<num>\d{1,3}(?:[,.]+\d{3})+|\d{1,3})원?$")
HAS_LETTER_RE = re.compile(r"[가-힣A-Za-z]")
MASKED_RE = re.compile(r"^\[[^\]]+\]$")
# 한 박스에 붙어 나온 "항목명 금액": 앞쪽에 글자가 있고 끝이 금액 패턴
COMBINED_RE = re.compile(r"^(?P<label>.*[가-힣A-Za-z)\]].*?)\s*(?P<amount>[-△▲]?(?:\d{1,3}(?:[,.]+\d{3})+|\d{1,3})원?)$")


def parse_amount(text: str) -> int | None:
    """금액이면 정수(원)로, 아니면 None."""
    m = AMOUNT_RE.match(re.sub(r"\s+", "", text))
    if not m:
        return None
    value = int(re.sub(r"\D", "", m.group("num")))
    return -value if m.group("sign") else value


def is_label(text: str) -> bool:
    return bool(HAS_LETTER_RE.search(text)) and not MASKED_RE.match(text.strip()) and parse_amount(text) is None


# ---- 결과 형식 ---------------------------------------------------------------

@dataclass
class Pair:
    label: str
    amount: int
    amount_text: str       # OCR이 읽은 원문 (예: "131,.630")
    confidence: float      # 항목명·금액 박스 신뢰도 중 낮은 값
    row: int
    label_box: TextBox
    amount_box: TextBox

    def to_dict(self) -> dict:
        return {"label": self.label, "amount": self.amount, "amount_text": self.amount_text,
                "confidence": self.confidence, "row": self.row}


@dataclass
class PairingResult:
    pairs: list[Pair] = field(default_factory=list)
    unmatched_amounts: list[TextBox] = field(default_factory=list)  # 항목명 없이 나온 금액
    rows: list[list[TextBox]] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "pairs": [p.to_dict() for p in self.pairs],
            "unmatched_amounts": [{"text": b.text, "amount": parse_amount(b.text), "score": b.score}
                                  for b in self.unmatched_amounts],
            "row_count": len(self.rows),
        }


# ---- 1. 붙어 나온 박스 쪼개기 ---------------------------------------------------

def _char_width(ch: str) -> float:
    if "가" <= ch <= "힣":
        return 1.0
    return 0.35 if ch.isspace() else 0.55


def split_combined(boxes: list[TextBox]) -> list[TextBox]:
    out = []
    for b in boxes:
        text = b.text.strip()
        m = COMBINED_RE.match(text)
        if not m or parse_amount(m.group("amount")) is None or not is_label(m.group("label").strip()):
            out.append(b)
            continue
        cut = m.start("amount")
        # 경계 바로 앞이 숫자·하이픈 등이면 사번("A-1024")이나 날짜("2026-03-25")의 일부이므로 쪼개지 않는다.
        # 마이너스 금액("공제 -12,000")은 앞에 공백이 있을 때만 인정한다.
        before = text[cut - 1] if cut > 0 else " "
        if before.isdigit() or before in "-./:~,_" or (text[cut] == "-" and not before.isspace()):
            out.append(b)
            continue
        # 글자 종류별 너비로 경계 X를 추정한다
        widths = [_char_width(c) for c in text]
        x_cut = b.x0 + (b.x1 - b.x0) * sum(widths[:cut]) / (sum(widths) or 1)
        out.append(replace(b, text=m.group("label").strip(), x1=int(x_cut)))
        out.append(replace(b, text=m.group("amount"), x0=int(x_cut)))
    return out


# ---- 2. 행 묶기 ---------------------------------------------------------------

def group_rows(boxes: list[TextBox]) -> list[list[TextBox]]:
    rows: list[list[TextBox]] = []
    for b in sorted(boxes, key=lambda b: b.cy):
        if rows:
            current = rows[-1]
            row_cy = mean(x.cy for x in current)
            # 바로 앞 박스가 아니라 행 평균과 비교해야 기울어진 사진에서 옆 행까지 이어 붙지 않는다
            tolerance = ROW_TOLERANCE * min(b.height, min(x.height for x in current))
            if abs(b.cy - row_cy) <= tolerance:
                current.append(b)
                continue
        rows.append([b])
    return [sorted(r, key=lambda b: b.x0) for r in rows]


# ---- 3~5. 행 안에서 짝짓기 -------------------------------------------------------

def pair(boxes: list[TextBox]) -> PairingResult:
    result = PairingResult()
    result.rows = group_rows(split_combined(boxes))

    for row_idx, row in enumerate(result.rows):
        pending: TextBox | None = None  # 아직 금액을 못 만난 항목명
        for b in row:
            amount = parse_amount(b.text)
            if amount is not None:
                if pending is not None:
                    result.pairs.append(Pair(
                        label=re.sub(r"\s+", "", pending.text), amount=amount, amount_text=b.text,
                        confidence=min(pending.score, b.score), row=row_idx,
                        label_box=pending, amount_box=b))
                    pending = None
                else:
                    result.unmatched_amounts.append(b)
            elif is_label(b.text):
                # 금액 없이 다음 항목명이 오면 앞의 것은 표 제목 등으로 보고 버린다
                pending = b
            else:
                # 날짜·사번·마스킹 값 등: 항목명과 금액 사이를 끊는다
                pending = None
    return result
