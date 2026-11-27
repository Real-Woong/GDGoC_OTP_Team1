"""입사일 추출 (라벨 기준).

계좌번호 마스킹과 같은 방식: '입사일' 같은 라벨을 먼저 찾고, 그 라벨과 같은 박스 → 같은 줄 오른쪽 →
바로 아래 줄 순서로 날짜를 찾는다. 라벨 없는 날짜(지급일 등)는 입사일로 보지 않는다.

입사일은 명세서에 없을 수도 있다(법정 필수 기재 항목이 아님). 찾으면 후보 값으로만 돌려주고
(status: needs_review), 최종 값은 사용자가 확인해서 백엔드의 User.joinedOn에 저장한다.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date

from ocr.mask import same_row
from ocr.recognize import TextBox

LABEL_RE = re.compile(r"입사(?:년월일|일자|일)?")

# 연도가 있는 날짜만 인정한다. "3/18 입사" 같은 메모는 연도가 없어서 잡지 않는다.
DATE_PATTERNS = [
    re.compile(r"(?<!\d)(?P<y>(?:19|20)\d{2})\s*년\s*(?P<m>\d{1,2})\s*월\s*(?P<d>\d{1,2})\s*일?"),
    re.compile(r"(?<!\d)(?P<y>(?:19|20)\d{2})\s*[.\-/]\s*(?P<m>\d{1,2})\s*[.\-/]\s*(?P<d>\d{1,2})(?!\d)"),
    re.compile(r"(?<![\d.\-/])(?P<y>\d{2})[.\-/](?P<m>\d{1,2})[.\-/](?P<d>\d{1,2})(?![\d.\-/])"),  # 26.03.18
]


@dataclass
class JoinDateResult:
    value: str | None        # "YYYY-MM-DD"
    source_text: str | None  # 날짜를 찾은 OCR 원문
    confidence: float | None

    def to_dict(self) -> dict:
        return {
            "value": self.value, "source_text": self.source_text, "confidence": self.confidence,
            # 찾았어도 사용자 확인이 필요하다
            "status": "needs_review" if self.value else "missing",
        }


def parse_date(text: str) -> str | None:
    for pattern in DATE_PATTERNS:
        for m in pattern.finditer(text):
            y = int(m.group("y"))
            if y < 100:  # 두 자리 연도: 26 → 2026, 98 → 1998
                y += 2000 if y <= date.today().year % 100 else 1900
            try:
                d = date(y, int(m.group("m")), int(m.group("d")))
            except ValueError:  # 2026-02-30 같은 없는 날짜
                continue
            if 1960 <= d.year <= date.today().year + 1:
                return d.isoformat()
    return None


def find_join_date(boxes: list[TextBox]) -> JoinDateResult:
    for i, label in enumerate(boxes):
        m = LABEL_RE.search(label.text)
        if not m:
            continue
        # (1) 같은 박스, 라벨 뒤: "입사일: 2026-03-18"
        found = parse_date(label.text[m.end():])
        if found:
            return JoinDateResult(found, label.text, label.score)
        # (2) 같은 줄 오른쪽 바로 옆 칸
        right = sorted((b for j, b in enumerate(boxes) if j != i and same_row(label, b) and b.x0 >= label.x1),
                       key=lambda b: b.x0)
        if right:
            found = parse_date(right[0].text)
            if found:
                return JoinDateResult(found, right[0].text, min(label.score, right[0].score))
        # (3) 바로 아래 줄 (라벨 아래에 값이 적힌 양식)
        below = [b for b in boxes if b.y0 >= label.y1 and b.y0 - label.y1 < 2 * label.height
                 and b.x0 < label.x1 + label.height * 4 and b.x1 > label.x0]
        if below:
            b = min(below, key=lambda b: b.y0)
            found = parse_date(b.text)
            if found:
                return JoinDateResult(found, b.text, min(label.score, b.score))
    return JoinDateResult(None, None, None)
