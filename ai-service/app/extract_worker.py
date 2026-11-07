"""Fixed parser subprocess with CPU/address-space limits; only structured results reach the parent."""
import json
import os
from pathlib import Path
import resource
import sys
from uuid import UUID
from app.extraction import extract, ExtractionError


def main():
    resource.setrlimit(resource.RLIMIT_CPU, (15, 15))
    resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024, 512 * 1024 * 1024))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    try:
        value = json.load(sys.stdin)
        pages = extract(Path(os.environ['DOCUMENT_ROOT']), UUID(value['projectId']), UUID(value['documentId']), value['mimeType'])
        result = {'pages': pages, 'code': 'TEXT_EXTRACTED_AI_PENDING'}
    except ExtractionError as error:
        result = {'pages': [], 'code': error.code}
    except Exception:
        result = {'pages': [], 'code': 'EXTRACTION_FAILED'}
    print(json.dumps(result))


if __name__ == '__main__':
    main()
