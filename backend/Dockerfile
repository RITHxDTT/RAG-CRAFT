FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    STORAGE_DIR=/app/storage

WORKDIR /app

# Cache dependencies independently of application changes.
COPY requirements.lock.txt .
RUN pip install --no-cache-dir -r requirements.lock.txt

COPY app ./app
COPY migrations ./migrations
COPY alembic.ini .
RUN mkdir -p /app/storage

EXPOSE 8000

# Compose runs migrations before starting the API and reuses this image for the worker.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
