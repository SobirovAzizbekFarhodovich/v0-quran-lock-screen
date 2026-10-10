FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY requirements.txt .
# Serverda GPU yo'q: CUDA'li torch (bir necha GB) o'rniga CPU versiyasini olamiz.
RUN pip install --no-cache-dir torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
COPY scripts ./scripts
COPY data ./data

EXPOSE 8002
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]
