"""OCR 3단계: PaddleOCR로 글자와 위치(박스)를 읽는다.

preprocess.py가 만든 흑백 이미지를 받아 TextBox 목록을 돌려준다.
항목명과 금액을 짝짓는 일은 다음 단계에서 한다.

처음 실행할 때 모델 파일(약 수십 MB)을 ~/.paddlex/official_models 에 내려받는다.
"""

from __future__ import annotations

import os
from dataclasses import asdict, dataclass

# 모델 서버 연결 확인을 건너뛴다(이미 받은 모델이 있으면 바로 사용)
os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")

import cv2  # noqa: E402
import numpy as np  # noqa: E402

# ---- 설정 -----------------------------------------------------------------

# 글자 영역 찾기 모델. 기본값인 server 모델은 CPU에서 1장에 2분 이상 걸려서 mobile을 쓴다.
DETECTION_MODEL = "PP-OCRv5_mobile_det"
# 글자 인식 모델(한국어). 모델 이름을 하나라도 직접 지정하면 PaddleOCR이 lang="korean"을
# 무시하고 한국어가 없는 기본 인식 모델을 쓰므로, 인식 모델도 반드시 이름으로 지정한다.
RECOGNITION_MODEL = "korean_PP-OCRv5_mobile_rec"
# 이보다 낮은 인식 결과는 버린다. 혼자 있는 "0"은 신뢰도가 0.4대로 나오기도 해서(case05 소득세)
# 너무 높게 잡으면 값이 사라진다. 낮은 신뢰도는 버리지 않고 짝의 confidence로 넘겨
# 사용자 확인(needs_review) 단계에서 걸러지게 한다.
MIN_SCORE = 0.3


@dataclass
class TextBox:
    text: str
    score: float  # 인식 신뢰도 0~1
    x0: int
    y0: int
    x1: int
    y1: int

    @property
    def cx(self) -> float:
        return (self.x0 + self.x1) / 2

    @property
    def cy(self) -> float:
        return (self.y0 + self.y1) / 2

    @property
    def height(self) -> int:
        return self.y1 - self.y0

    def to_dict(self) -> dict:
        return asdict(self)


_engine = None


def get_engine():
    """모델 로딩에 수십 초가 걸리므로 한 번만 만들어서 계속 쓴다."""
    global _engine
    if _engine is None:
        from paddleocr import PaddleOCR

        _engine = PaddleOCR(
            text_detection_model_name=DETECTION_MODEL,
            text_recognition_model_name=RECOGNITION_MODEL,
            # 전처리 단계에서 이미 회전·원근을 보정했으므로 PaddleOCR 자체 보정은 끈다
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
        )
    return _engine


def recognize(image: np.ndarray) -> list[TextBox]:
    """흑백 또는 BGR 이미지에서 글자 박스를 읽어 위→아래, 왼쪽→오른쪽 순서로 돌려준다."""
    if image.ndim == 2:
        image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)

    result = get_engine().predict(image)[0]
    boxes = []
    for text, score, (x0, y0, x1, y1) in zip(result["rec_texts"], result["rec_scores"], result["rec_boxes"]):
        text = text.strip()
        if text and score >= MIN_SCORE:
            boxes.append(TextBox(text, round(float(score), 4), int(x0), int(y0), int(x1), int(y1)))
    boxes.sort(key=lambda b: (b.cy, b.x0))
    return boxes
