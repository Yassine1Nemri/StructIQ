"""Untrusted document parsing. No provider calls, tools, or document instructions are executed."""
from io import BytesIO
from pathlib import Path
from uuid import UUID
from pypdf import PdfReader

MAX_FILE_BYTES = 10 * 1024 * 1024
MAX_TEXT_CHARS = 500_000
MAX_PAGES = 200


class ExtractionError(Exception):
    def __init__(self, code: str):
        self.code = code


def plain_text(data: bytes) -> str:
    if data.startswith((b'MZ', b'\x7fELF', b'PK\x03\x04', b'%PDF-', b'#!')):
        raise ExtractionError('INVALID_TEXT')
    try:
        text = data.decode('utf-8-sig')
    except UnicodeDecodeError:
        raise ExtractionError('INVALID_TEXT') from None
    if any(ord(c) < 32 and c not in '\n\r\t' for c in text):
        raise ExtractionError('INVALID_TEXT')
    if text.lstrip().lower().startswith(('<!doctype', '<html', '<script', '<?xml')):
        raise ExtractionError('INVALID_TEXT')
    return text.replace('\x00', '').strip()


def extract(root: Path, project_id: UUID, document_id: UUID, mime_type: str) -> list[dict]:
    root = root.resolve()
    folder = root / str(project_id)
    path = folder / str(document_id)
    if folder.is_symlink() or path.is_symlink() or not path.resolve().is_relative_to(root):
        raise ExtractionError('INVALID_STORAGE')
    if not path.is_file() or path.stat().st_size > MAX_FILE_BYTES:
        raise ExtractionError('INVALID_STORAGE')
    data = path.read_bytes()
    if mime_type == 'text/plain':
        pages = [{'pageNumber': 1, 'text': plain_text(data)}]
    elif mime_type == 'application/pdf':
        if not data.startswith(b'%PDF-'):
            raise ExtractionError('INVALID_PDF')
        try:
            reader = PdfReader(BytesIO(data), strict=True)
            if reader.is_encrypted:
                raise ExtractionError('ENCRYPTED_PDF')
            if len(reader.pages) > MAX_PAGES:
                raise ExtractionError('EXTRACTION_LIMIT')
            pages = []
            total = 0
            for number, page in enumerate(reader.pages, 1):
                text = (page.extract_text() or '').replace('\x00', '').strip()
                total += len(text)
                if total > MAX_TEXT_CHARS:
                    raise ExtractionError('EXTRACTION_LIMIT')
                if text:
                    pages.append({'pageNumber': number, 'text': text})
        except ExtractionError:
            raise
        except Exception:
            raise ExtractionError('INVALID_PDF') from None
    else:
        raise ExtractionError('INVALID_TYPE')
    if sum(len(page['text']) for page in pages) > MAX_TEXT_CHARS:
        raise ExtractionError('EXTRACTION_LIMIT')
    pages = [page for page in pages if page['text']]
    if not pages:
        raise ExtractionError('NO_TEXT')
    return pages
