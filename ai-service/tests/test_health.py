from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health_without_provider_key(monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_health_does_not_expose_secrets(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "test-secret-do-not-expose")
    assert "test-secret" not in client.get("/health").text


def test_no_functional_ai_or_documentation_routes():
    for path in ("/ask", "/docs", "/openapi.json"):
        assert client.get(path).status_code == 404
