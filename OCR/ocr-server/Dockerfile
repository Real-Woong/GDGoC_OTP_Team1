# 급여명세서 OCR API
# 빌드: docker build -t payslip-ocr .
# 실행: docker run -p 8000:8000 -e OCR_API_KEY=<내부 키> payslip-ocr
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True

# PaddlePaddle·OpenCV 실행에 필요한 시스템 라이브러리
RUN apt-get update \
 && apt-get install -y --no-install-recommends libgomp1 libglib2.0-0 \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

RUN useradd --create-home app
COPY ocr ./ocr
COPY api ./api
USER app

# OCR 모델을 이미지에 미리 받아 둔다. 실행할 때 인터넷 연결이 없어도 되고 첫 시작이 빨라진다.
RUN python -c "from ocr.recognize import get_engine; get_engine()"

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"
CMD ["uvicorn", "api.main:app", "--host", "0.0.0.0", "--port", "8000"]
