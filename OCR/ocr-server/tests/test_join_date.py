"""입사일 추출 테스트 (박스 좌표만으로)."""

import pytest

from ocr.join_date import find_join_date, parse_date
from ocr.recognize import TextBox


def box(text, x0=0, y0=0, x1=150, y1=40):
    return TextBox(text, 0.99, x0, y0, x1, y1)


@pytest.mark.parametrize("text,expected", [
    ("2026-03-18", "2026-03-18"),
    ("2026.03.18", "2026-03-18"),
    ("2026/3/8", "2026-03-08"),
    ("2026년 3월 18일", "2026-03-18"),
    ("2026년3월18일", "2026-03-18"),
    ("26.03.18", "2026-03-18"),
    ("98.03.02", "1998-03-02"),
    ("2026-02-30", None),          # 없는 날짜
    ("3/18", None),                # 연도 없음
    ("1,354,830", None),           # 금액
    ("20250312", None),            # 사번
    ("[주민등록번호]", None),
], ids=lambda x: x if isinstance(x, str) else "")
def test_parse_date(text, expected):
    assert parse_date(text) == expected


@pytest.mark.parametrize("name,boxes,expected", [
    ("같은 줄 오른쪽 칸", [box("입사일", 0, 0, 100, 40), box("2025-03-12", 200, 2, 400, 38)], "2025-03-12"),
    ("같은 박스", [box("입사일자: 2025.03.12", 0, 0, 300, 40)], "2025-03-12"),
    ("라벨 아래 줄", [box("입사년월일", 0, 0, 150, 40), box("2025년 3월 12일", 0, 50, 250, 90)], "2025-03-12"),
    ("라벨 없는 날짜는 아님(지급일)", [box("지급일", 0, 0, 100, 40), box("2026-04-10", 200, 2, 400, 38)], None),
    ("메모의 '3/18 입사'는 연도가 없어 아님",
     [box("※3/18 입사,3/18~3/31일할계산(월 기본급3,000,000원)", 0, 0, 700, 40)], None),
    ("입사일 칸이 비어 있음", [box("입사일", 0, 0, 100, 40), box("사번", 200, 2, 300, 38)], None),
    ("다른 줄의 날짜는 아님",
     [box("입사일", 0, 0, 100, 40), box("2026-04-10", 200, 300, 400, 340)], None),
], ids=lambda x: x if isinstance(x, str) else "")
def test_find_join_date(name, boxes, expected):
    result = find_join_date(boxes)
    assert result.value == expected
    assert result.to_dict()["status"] == ("needs_review" if expected else "missing")
