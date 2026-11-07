FROM python:3.12-slim-bookworm AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /app
COPY pyproject.toml .
COPY app app
RUN pip install .
RUN mkdir -p /data/documents && chown 10001:10001 /data/documents && chmod 0750 /data/documents

FROM base AS test
COPY tests tests
RUN pip install '.[test]' && pytest -q

FROM base AS runtime
RUN useradd --system --uid 10002 --create-home structiq
USER 10002
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
