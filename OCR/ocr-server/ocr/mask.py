"""OCR 4단계: 개인정보 탐지와 마스킹.

OCR 결과(TextBox 목록)에서 개인정보를 찾고, 이미지의 해당 영역을 Pillow로 칠하고,
글자 결과에서도 그 부분을 지운다.

탐지 대상
  - 주민등록번호: 정규식 + 생년월일·성별 자리 검증
  - 전화번호: 정규식 (휴대폰·지역번호)
  - 이메일: 정규식
  - 계좌번호: 계좌 관련 라벨(지급계좌, 은행 등)을 먼저 찾고, 그 라벨과 같은 박스·같은 줄
             오른쪽·바로 아래 줄에 있는 숫자 중 길이(숫자 10~14자리)가 맞는 것만
탐지하지 않음: 성명, 주소

마스킹한 원래 값은 어디에도 남기지 않는다(로그·결과 JSON 포함). 종류와 위치만 남긴다.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, replace

import numpy as np
from PIL import Image, ImageDraw

from ocr.recognize import TextBox

# ---- 설정 -----------------------------------------------------------------

ACCOUNT_DIGITS_MIN = 10   # 국내 계좌번호는 보통 숫자 10~14자리
ACCOUNT_DIGITS_MAX = 14
MASK_PADDING = 6          # 칠할 때 박스 바깥으로 더 칠하는 여백(px)
MASK_FILL = 0             # 검은색

# ---- 정규식 ---------------------------------------------------------------

# 주민등록번호: YYMMDD-GNNNNNN. 하이픈 없이 붙은 13자리도 잡는다. 뒷자리 일부가 * 로 가려진 형태도 포함
RRN_RE = re.compile(r"(?<![\d*])(\d{2})(\d{2})(\d{2})\s*-?\s*([1-8])[\d*]{6}(?![\d*])")

# 전화번호: 휴대폰(010 등) / 서울 02 / 지역번호 031~064 / 070. 구분자는 - . 공백 또는 없음
PHONE_RE = re.compile(
    r"(?<!\d)(?:01[016789]|02|0[3-6][1-5]|070)[-.\s]?\d{3,4}[-.\s]?\d{4}(?!\d)"
)

EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")

# 계좌 라벨. "은행"은 "예시은행 000-00..."처럼 값 박스 안에 은행명으로 같이 나오는 경우가 많아서 포함
ACCOUNT_LABEL_RE = re.compile(r"계좌|입금|은행")
# 계좌번호 후보: 숫자로 시작하고 끝나며 중간에 숫자·하이픈·공백만 있는 덩어리
ACCOUNT_NUMBER_RE = re.compile(r"(?<![\d-])\d[\d\s-]*\d(?![\d-])")


@dataclass
class Detection:
    kind: str            # "주민등록번호" | "전화번호" | "이메일" | "계좌번호"
    box_index: int       # 어느 TextBox에서 찾았는지
    start: int           # 박스 글자 안에서의 위치
    end: int
    bbox: tuple[int, int, int, int]  # 이미지에서 칠할 영역


@dataclass
class MaskResult:
    image: Image.Image          # 마스킹된 이미지
    boxes: list[TextBox]        # 개인정보 부분이 [종류]로 바뀐 OCR 결과
    detections: list[Detection]

    def summary(self) -> dict:
        """로그·응답용 요약. 원래 값은 넣지 않는다."""
        counts: dict[str, int] = {}
        for d in self.detections:
            counts[d.kind] = counts.get(d.kind, 0) + 1
        return counts


# ---- 탐지 -----------------------------------------------------------------

def is_valid_rrn_date(mm: str, dd: str) -> bool:
    return 1 <= int(mm) <= 12 and 1 <= int(dd) <= 31


def find_pattern_matches(text: str) -> list[tuple[str, int, int]]:
    """한 박스의 글자에서 주민번호·전화번호·이메일을 찾는다."""
    found = []
    for m in RRN_RE.finditer(text):
        if is_valid_rrn_date(m.group(2), m.group(3)):
            found.append(("주민등록번호", m.start(), m.end()))
    for m in PHONE_RE.finditer(text):
        if not overlaps(found, m.start(), m.end()):
            found.append(("전화번호", m.start(), m.end()))
    for m in EMAIL_RE.finditer(text):
        found.append(("이메일", m.start(), m.end()))
    return found


def account_numbers_in(text: str, after: int = 0) -> list[tuple[int, int]]:
    """글자에서 길이 검증을 통과한 계좌번호 후보의 위치를 돌려준다."""
    spans = []
    for m in ACCOUNT_NUMBER_RE.finditer(text, after):
        digits = re.sub(r"\D", "", m.group())
        if ACCOUNT_DIGITS_MIN <= len(digits) <= ACCOUNT_DIGITS_MAX:
            spans.append((m.start(), m.end()))
    return spans


def same_row(a: TextBox, b: TextBox) -> bool:
    overlap = min(a.y1, b.y1) - max(a.y0, b.y0)
    return overlap > 0.5 * min(a.height, b.height)


def find_accounts(boxes: list[TextBox]) -> list[tuple[int, int, int]]:
    """계좌 라벨을 먼저 찾고, 그 주변에서 계좌번호를 찾는다. (박스 번호, 시작, 끝) 목록."""
    found: set[tuple[int, int, int]] = set()
    for i, label in enumerate(boxes):
        m = ACCOUNT_LABEL_RE.search(label.text)
        if not m:
            continue

        # (1) 라벨과 같은 박스 안, 라벨 뒤쪽: "지급계좌: 국민 123-45-678901", "예시은행 000-0000-..."
        hits = [(i, s, e) for s, e in account_numbers_in(label.text, after=m.end())]

        # (2) 같은 줄, 라벨 오른쪽 박스: 표에서 라벨 칸 | 값 칸 으로 나뉜 경우
        if not hits:
            right = sorted((j for j, b in enumerate(boxes) if j != i and same_row(label, b) and b.x0 >= label.x1),
                           key=lambda j: boxes[j].x0)
            for j in right[:2]:  # 바로 옆 1~2칸까지만 본다
                hits += [(j, s, e) for s, e in account_numbers_in(boxes[j].text)]
                if hits:
                    break

        # (3) 바로 아래 줄: 라벨 아래에 값이 적힌 양식
        if not hits:
            below = [j for j, b in enumerate(boxes)
                     if b.y0 >= label.y1 and b.y0 - label.y1 < 2 * label.height
                     and b.x0 < label.x1 + label.height * 4 and b.x1 > label.x0]
            for j in sorted(below, key=lambda j: boxes[j].y0)[:1]:
                hits += [(j, s, e) for s, e in account_numbers_in(boxes[j].text)]

        found.update(hits)
    return sorted(found)


def detect(boxes: list[TextBox]) -> list[tuple[str, int, int, int]]:
    """(종류, 박스 번호, 시작, 끝) 목록."""
    results = []
    for i, b in enumerate(boxes):
        results += [(kind, i, s, e) for kind, s, e in find_pattern_matches(b.text)]
    for i, s, e in find_accounts(boxes):
        same_box = [(s2, e2) for _, j, s2, e2 in results if j == i]
        if not overlaps(same_box, s, e):  # 이미 전화번호 등으로 잡힌 부분은 중복으로 넣지 않음
            results.append(("계좌번호", i, s, e))
    return results


def overlaps(spans, start: int, end: int) -> bool:
    return any(not (end <= s[-2] or start >= s[-1]) for s in spans)


# ---- 글자 위치 → 픽셀 위치 -------------------------------------------------------

def char_width(ch: str) -> float:
    """한글은 넓고 숫자·기호는 좁다. 박스 안에서 글자 위치를 추정할 때 쓰는 상대 너비."""
    if "가" <= ch <= "힣":
        return 1.0
    if ch.isspace():
        return 0.35
    return 0.55


def span_bbox(box: TextBox, start: int, end: int, image_size: tuple[int, int]) -> tuple[int, int, int, int]:
    """박스 글자 중 [start, end) 부분이 이미지에서 차지하는 영역을 추정한다.

    PaddleOCR은 글자 줄 단위 박스만 주므로, 글자 종류별 너비로 비율을 계산한다.
    추정이 빗나가도 개인정보가 드러나지 않도록 여백을 넉넉히 준다.
    """
    widths = [char_width(c) for c in box.text]
    total = sum(widths) or 1
    left = box.x0 + (box.x1 - box.x0) * sum(widths[:start]) / total
    right = box.x0 + (box.x1 - box.x0) * sum(widths[:end]) / total
    pad = MASK_PADDING + box.height * 0.3  # 추정 오차를 감안해 좌우로 더 넓게
    w, h = image_size
    return (max(0, int(left - pad)), max(0, box.y0 - MASK_PADDING),
            min(w, int(right + pad)), min(h, box.y1 + MASK_PADDING))


# ---- 마스킹 ---------------------------------------------------------------

def mask(image: np.ndarray | Image.Image, boxes: list[TextBox]) -> MaskResult:
    pil = image.copy() if isinstance(image, Image.Image) else Image.fromarray(image)
    draw = ImageDraw.Draw(pil)

    detections = []
    for kind, i, s, e in detect(boxes):
        bbox = span_bbox(boxes[i], s, e, pil.size)
        draw.rectangle(bbox, fill=MASK_FILL)
        detections.append(Detection(kind, i, s, e, bbox))

    # 글자 결과에서도 지운다. 같은 박스에 여러 개가 있으면 뒤에서부터 바꿔야 위치가 안 밀린다
    new_boxes = list(boxes)
    for d in sorted(detections, key=lambda d: (d.box_index, -d.start)):
        b = new_boxes[d.box_index]
        new_boxes[d.box_index] = replace(b, text=b.text[:d.start] + f"[{d.kind}]" + b.text[d.end:])

    return MaskResult(image=pil, boxes=new_boxes, detections=detections)
