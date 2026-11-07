"""Test image only. Never imported or packaged into the production runtime image."""
from app.main import create_app
from app.rag import RagService
from tests.test_rag import FakeProvider
app = create_app(rag=RagService(provider=FakeProvider()))
