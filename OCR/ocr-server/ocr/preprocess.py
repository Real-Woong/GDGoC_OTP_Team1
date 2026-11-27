"""OCR 2단계: 페이지 이미지 전처리 (Pillow + OpenCV).

prepare.py가 만든 페이지 이미지를 받아 OCR이 읽기 좋은 흑백 이미지로 만든다.

사진(PNG/JPG)          PDF 렌더링 이미지
  1. 문서 영역 찾기·원근 보정      (건너뜀: 이미 반듯함)
  2. 흑백 변환                   2. 흑백 변환
  3. 기울기 보정                  (건너뜀)
  4. 그림자·조명 보정              (건너뜀)
  5. 크기 맞추기                  5. 크기 맞추기

이진화(흑/백 두 값)는 하지 않는다. PaddleOCR은 회색조 이미지를 더 잘 읽는다.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import cv2
import numpy as np
from PIL import Image

# ---- 설정 -----------------------------------------------------------------

# 결과 이미지의 긴 변 길이(px). 작으면 키우고 크면 줄인다.
# 샘플 5건 측정: 1000px 8초·금액 57/62, 1400px 8초·62/62, 2000px 9초·61/62 → 1400px 선택
TARGET_LONG_SIDE = 1400
MIN_DOC_AREA_RATIO = 0.25    # 사진에서 이 비율보다 작은 사각형은 문서로 보지 않는다
MAX_SKEW_DEG = 10.0          # 이보다 큰 기울기는 오검출로 보고 무시한다
MIN_SKEW_DEG = 0.2           # 이보다 작은 기울기는 보정하지 않는다


@dataclass
class PreprocessedPage:
    image: np.ndarray                          # 흑백(uint8, H×W)
    steps: list[str] = field(default_factory=list)  # 적용한 단계 기록 (디버깅용)
    skew_deg: float = 0.0
    perspective_fixed: bool = False

    def to_pil(self) -> Image.Image:
        return Image.fromarray(self.image)


def preprocess(page: Image.Image, source_format: str) -> PreprocessedPage:
    """페이지 이미지 1장을 전처리한다. source_format은 "pdf" | "png" | "jpg"."""
    bgr = cv2.cvtColor(np.asarray(page.convert("RGB")), cv2.COLOR_RGB2BGR)
    result = PreprocessedPage(image=bgr)

    is_photo = source_format in ("png", "jpg")
    if is_photo:
        warped = find_and_warp_document(bgr)
        if warped is not None:
            bgr = warped
            result.perspective_fixed = True
            result.steps.append("원근 보정")

    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    result.steps.append("흑백 변환")

    if is_photo:
        angle = estimate_skew(gray)
        if abs(angle) >= MIN_SKEW_DEG:
            gray = rotate(gray, angle)
            result.skew_deg = angle
            result.steps.append(f"기울기 보정({angle:+.2f}°)")

        gray = remove_shadow(gray)
        result.steps.append("그림자·조명 보정")

    gray = resize_long_side(gray, TARGET_LONG_SIDE)
    result.steps.append(f"크기 조정(긴 변 {TARGET_LONG_SIDE}px)")

    result.image = gray
    return result


# ---- 1. 문서 영역 찾기 + 원근 보정 -------------------------------------------

def find_and_warp_document(bgr: np.ndarray) -> np.ndarray | None:
    """배경(책상 등) 위에 놓인 종이의 네 모서리를 찾아 반듯하게 편다. 못 찾으면 None."""
    h, w = bgr.shape[:2]
    # 계산을 빠르게 하려고 작게 줄여서 찾고, 좌표만 원래 크기로 되돌린다
    scale = 1000 / max(h, w) if max(h, w) > 1000 else 1.0
    small = cv2.resize(bgr, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)

    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (5, 5), 0)
    # 종이(밝음)와 배경(어두움)을 나눈다
    _, mask = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    # 글자·표 선 때문에 생긴 구멍을 메운다
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((15, 15), np.uint8))

    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    largest = max(contours, key=cv2.contourArea)
    area_ratio = cv2.contourArea(largest) / (small.shape[0] * small.shape[1])
    # 너무 작으면 문서가 아니고, 화면을 거의 다 채우면 이미 잘린 스캔 이미지다
    if area_ratio < MIN_DOC_AREA_RATIO or area_ratio > 0.97:
        return None

    peri = cv2.arcLength(largest, True)
    approx = cv2.approxPolyDP(largest, 0.02 * peri, True)
    if len(approx) != 4:
        return None

    corners = order_corners(approx.reshape(4, 2).astype(np.float32) / scale)
    tl, tr, br, bl = corners
    out_w = int(max(np.linalg.norm(tr - tl), np.linalg.norm(br - bl)))
    out_h = int(max(np.linalg.norm(bl - tl), np.linalg.norm(br - tr)))
    dst = np.array([[0, 0], [out_w - 1, 0], [out_w - 1, out_h - 1], [0, out_h - 1]], dtype=np.float32)
    matrix = cv2.getPerspectiveTransform(corners, dst)
    return cv2.warpPerspective(bgr, matrix, (out_w, out_h), flags=cv2.INTER_CUBIC,
                               borderMode=cv2.BORDER_REPLICATE)


def order_corners(pts: np.ndarray) -> np.ndarray:
    """네 점을 좌상, 우상, 우하, 좌하 순서로 정렬한다."""
    s = pts.sum(axis=1)
    d = np.diff(pts, axis=1).ravel()
    return np.array([pts[np.argmin(s)], pts[np.argmin(d)], pts[np.argmax(s)], pts[np.argmax(d)]],
                    dtype=np.float32)


# ---- 3. 기울기 보정 -----------------------------------------------------------

def estimate_skew(gray: np.ndarray) -> float:
    """표의 가로선 기울기로 문서가 몇 도 기울었는지 추정한다. 못 찾으면 0."""
    h, w = gray.shape
    edges = cv2.Canny(gray, 50, 150)
    lines = cv2.HoughLinesP(edges, 1, np.pi / 1800, threshold=150,
                            minLineLength=int(w * 0.3), maxLineGap=10)
    if lines is None:
        return 0.0
    angles = []
    for x1, y1, x2, y2 in lines.reshape(-1, 4):
        angle = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        if abs(angle) <= MAX_SKEW_DEG:  # 가로선만 사용
            angles.append(angle)
    return float(np.median(angles)) if angles else 0.0


def rotate(gray: np.ndarray, angle: float) -> np.ndarray:
    h, w = gray.shape
    matrix = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    return cv2.warpAffine(gray, matrix, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)


# ---- 4. 그림자·조명 보정 -------------------------------------------------------

def remove_shadow(gray: np.ndarray) -> np.ndarray:
    """글자를 지운 '배경 밝기'를 추정해서 나눠 준다. 그림자와 조명 차이가 평평해진다."""
    # 사진 노이즈를 먼저 줄인다. 그대로 두면 나눗셈 후 얼룩처럼 보인다.
    denoised = cv2.fastNlMeansDenoising(gray, None, h=7, templateWindowSize=7, searchWindowSize=21)
    # 닫힘 연산으로 어두운 글자만 지운다. 팽창(dilate)만 쓰면 밝은 영역이 그림자 쪽으로
    # 몇 px 번져서 그림자 경계에 검은 선이 생긴다.
    background = cv2.morphologyEx(denoised, cv2.MORPH_CLOSE, np.ones((11, 11), np.uint8))
    background = cv2.medianBlur(background, 21)  # 중앙값 필터는 그림자 경계(계단)를 유지한다
    return cv2.divide(denoised, background, scale=255)


# ---- 5. 크기 맞추기 -----------------------------------------------------------

def resize_long_side(gray: np.ndarray, target: int) -> np.ndarray:
    h, w = gray.shape
    scale = target / max(h, w)
    if abs(scale - 1) < 0.02:
        return gray
    interp = cv2.INTER_CUBIC if scale > 1 else cv2.INTER_AREA
    return cv2.resize(gray, None, fx=scale, fy=scale, interpolation=interp)
