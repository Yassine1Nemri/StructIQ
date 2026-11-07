import asyncio
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from uuid import UUID
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, ValidationError
from app.rag import RagService
from app.provider import AiError


class HealthResponse(BaseModel):
    status: str


class ExtractRequest(BaseModel):
    model_config = ConfigDict(extra='forbid')
    projectId: UUID
    documentId: UUID
    mimeType: str


def create_app(root: Path | None = None, secret: str | None = None, rag=None) -> FastAPI:
    service = FastAPI(title='StructIQ internal AI service', docs_url=None, redoc_url=None, openapi_url=None)
    signing_secret = secret if secret is not None else os.getenv('INTERNAL_AI_SHARED_SECRET', '')
    document_root = root if root is not None else Path(os.getenv('DOCUMENT_ROOT', '/data/documents'))
    seen: dict[str, float] = {}
    slots = asyncio.Semaphore(2)
    rag = rag or RagService()

    @service.get('/health', response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(status='UP')

    @service.post('/internal/{operation}')
    async def extraction(operation: str, request: Request):
        if len(signing_secret.encode()) < 32:
            return JSONResponse({'code': 'SERVICE_NOT_CONFIGURED'}, status_code=503)
        body = bytearray()
        async for part in request.stream():
            body.extend(part)
            if len(body) > 12000:
                return JSONResponse({'code': 'INVALID_REQUEST'}, status_code=400)
        now = time.time()
        timestamp = request.headers.get('X-Internal-Timestamp', '')
        nonce = request.headers.get('X-Internal-Nonce', '')
        try:
            if abs(now - int(timestamp)) > 30 or str(UUID(nonce)) != nonce:
                raise ValueError()
        except ValueError:
            return JSONResponse({'code': 'UNAUTHENTICATED'}, status_code=401)
        canonical = f'POST\n{request.url.path}\n{timestamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}'
        expected = hmac.new(signing_secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, request.headers.get('X-Internal-Signature', '')):
            return JSONResponse({'code': 'UNAUTHENTICATED'}, status_code=401)
        for key in [key for key, expiry in seen.items() if expiry < now]:
            del seen[key]
        if nonce in seen:
            return JSONResponse({'code': 'UNAUTHENTICATED'}, status_code=401)
        if len(seen) >= 10_000:
            return JSONResponse({'code': 'SERVICE_BUSY'}, status_code=503)
        seen[nonce] = now + 61
        if operation in ('status', 'index', 'ask'):
            try:
                value = json.loads(body)
                project = UUID(value['projectId'])
                expected_fields = {'projectId'} | ({'documentId'} if operation == 'index' else {'question'} if operation == 'ask' else set())
                if set(value) != expected_fields:
                    raise ValueError()
                if operation == 'index':
                    document = UUID(value['documentId'])
                if operation == 'ask' and (not isinstance(value['question'], str) or not 1 <= len(value['question'].strip()) <= 2000):
                    raise ValueError()
            except (ValueError, KeyError, TypeError):
                return JSONResponse({'code': 'INVALID_REQUEST'}, status_code=400)
            if operation == 'status':
                return {'configured': rag.configured()}
            try:
                await asyncio.wait_for(slots.acquire(), timeout=0.1)
            except TimeoutError:
                return JSONResponse({'code': 'AI_BUSY'}, status_code=503)
            try:
                if operation == 'index':
                    return await asyncio.to_thread(rag.index, project, document)
                return await asyncio.to_thread(rag.ask, project, value['question'].strip())
            except AiError as error:
                return JSONResponse({'code': error.code}, status_code=error.status)
            except Exception:
                return JSONResponse({'code': 'AI_UNAVAILABLE'}, status_code=503)
            finally:
                slots.release()
        if operation != 'extract':
            return JSONResponse({'code': 'NOT_FOUND'}, status_code=404)
        try:
            value = ExtractRequest.model_validate_json(body)
            if value.mimeType not in ('application/pdf', 'text/plain'):
                raise ValueError()
        except (ValidationError, ValueError):
            return JSONResponse({'code': 'INVALID_REQUEST'}, status_code=400)
        try:
            await asyncio.wait_for(slots.acquire(), timeout=0.1)
        except TimeoutError:
            return JSONResponse({'code': 'SERVICE_BUSY'}, status_code=503)
        try:
            env = os.environ.copy()
            env['DOCUMENT_ROOT'] = str(document_root)
            # No private text or parser diagnostics are logged. Timeout kills and reaps the child.
            result = await asyncio.to_thread(subprocess.run, [sys.executable, '-m', 'app.extract_worker'],
                input=json.dumps(value.model_dump(mode='json')), text=True, stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL, timeout=20, env=env, check=False)
            if result.returncode != 0:
                return {'pages': [], 'code': 'EXTRACTION_LIMIT'}
            return json.loads(result.stdout)
        except subprocess.TimeoutExpired:
            return {'pages': [], 'code': 'EXTRACTION_TIMEOUT'}
        except Exception:
            return {'pages': [], 'code': 'EXTRACTION_FAILED'}
        finally:
            slots.release()

    return service


app = create_app()
