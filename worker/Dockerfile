FROM python:3.12-slim

# Install system dependencies required for pdf2image/poppler and psycopg2
RUN apt-get update && apt-get install -y \
    build-essential \
    libpq-dev \
    gcc \
    poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Environment setup
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app

# Start the worker
CMD ["python", "worker.py"]
