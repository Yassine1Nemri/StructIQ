"""Exercise real document extraction and isolation with disposable synthetic projects."""
import hashlib
import json
from pathlib import Path
import subprocess
from uuid import UUID
import urllib.error
import urllib.request
from demo_documents import meeting_pdf, pdf_bytes

password = json.loads((Path(__file__).resolve().parents[1] / '.demo-credentials.json').read_text())['password']
base = 'http://localhost:5173/api'


def call(method, path, token=None, payload=None, expected=200, file=None, binary=False):
    headers = {}
    data = None
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if file:
        name, mime, content = file
        boundary = 'structiq-phase3-fixture'
        headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
        data = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: {mime}\r\n\r\n'.encode()
                + content + f'\r\n--{boundary}--\r\n'.encode())
    elif payload is not None:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(payload).encode()
    request = urllib.request.Request(base + path, method=method, headers=headers, data=data)
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            code, raw, response_headers = response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        code, raw, response_headers = error.code, error.read(), error.headers
    assert code == expected, f'{method} {path}: expected {expected}, got {code}'
    return (raw, response_headers) if binary else (json.loads(raw) if raw else None)


def login(name):
    return call('POST', '/auth/login', payload={'email': name + '@demo.local', 'password': password})


def no_private_file(project, document):
    project, document = str(UUID(project)), str(UUID(document))
    subprocess.run(['docker', 'compose', 'exec', '-T', 'backend', 'sh', '-c', 'test ! -e "$1"', 'sh',
                    f'/var/lib/structiq/documents/{project}/{document}'], check=True)


manager, engineer, outsider = (login(name) for name in ('manager', 'engineer', 'outsider'))
mt, et, ot = (s['accessToken'] for s in (manager, engineer, outsider))
project = call('POST', '/projects', mt, {'name': 'Phase 3 document verification', 'status': 'ACTIVE'}, 201)['id']
other = None
tracked = []
try:
    other = call('POST', '/projects', ot, {'name': 'Other document verification', 'status': 'ACTIVE'}, 201)['id']
    call('POST', f'/projects/{project}/members', mt, {'email': 'engineer@demo.local', 'role': 'MEMBER'}, 201)
    pdf = meeting_pdf()
    doc = call('POST', f'/projects/{project}/documents', mt, expected=201, file=('meeting.pdf', 'application/pdf', pdf))
    tracked.append(doc['id'])
    assert doc['processingStatus'] == 'UPLOADED' and doc['processingCode'] == 'TEXT_EXTRACTED_AI_PENDING'
    assert doc['extractedPages'] == 2, f'Expected two extracted pages, got {doc["extractedPages"]}'
    raw, headers = call('GET', f'/projects/{project}/documents/{doc["id"]}/download', et, binary=True)
    assert hashlib.sha256(raw).digest() == hashlib.sha256(pdf).digest()
    assert 'attachment' in headers['Content-Disposition'] and headers['X-Content-Type-Options'] == 'nosniff'
    listing = call('GET', f'/projects/{project}/documents', mt)
    assert not {'storageKey', 'storage_key', 'checksum_sha256', 'content'} & listing['items'][0].keys()
    assert 'FICTIONAL Riverside' not in json.dumps(listing)
    call('GET', f'/projects/{project}/documents', ot, expected=404)
    call('GET', f'/projects/{project}/documents/{doc["id"]}/download', ot, expected=404)
    call('GET', f'/projects/{project}/documents/{doc["id"]}/download', expected=401)
    call('POST', f'/projects/{other}/members', ot, {'email': 'manager@demo.local', 'role': 'MANAGER'}, 201)
    call('GET', f'/projects/{other}/documents/{doc["id"]}/download', mt, expected=404)
    call('DELETE', f'/projects/{other}/documents/{doc["id"]}', mt, expected=404)
    text = call('POST', f'/projects/{project}/documents', et, expected=201,
                file=('../../meeting.txt', 'text/plain', b'FICTIONAL meeting notes: engineer owns inspection.'))
    tracked.append(text['id'])
    assert '/' not in text['filename'] and text['extractedPages'] == 1
    call('DELETE', f'/projects/{project}/documents/{doc["id"]}', et, expected=403)
    failed = call('POST', f'/projects/{project}/documents', mt, expected=201, file=('scan.pdf', 'application/pdf', pdf_bytes([''])))
    tracked.append(failed['id'])
    assert failed['processingStatus'] == 'FAILED' and failed['processingCode'] == 'NO_TEXT'
    assert call('POST', f'/projects/{project}/documents/{failed["id"]}/retry', mt)['processingCode'] == 'NO_TEXT'
    call('POST', f'/projects/{project}/documents', mt, expected=400, file=('bad.txt', 'text/plain', b'\x00binary'))
    call('POST', f'/projects/{project}/documents', mt, expected=400, file=('bad.pdf', 'application/pdf', b'MZexecutable'))
    call('POST', f'/projects/{project}/documents', mt, expected=413, file=('large.txt', 'text/plain', b'a' * (10*1024*1024+1)))
    assert call('POST', f'/projects/{project}/documents', mt, expected=413, file=('proxy-limit.txt', 'text/plain', b'a' * (12*1024*1024)))['code'] == 'FILE_TOO_LARGE'
    call('PATCH', f'/projects/{project}/members/{engineer["user"]["id"]}', mt, {'role': 'VIEWER'}, 204)
    call('POST', f'/projects/{project}/documents', et, expected=403, file=('blocked.txt', 'text/plain', b'notes'))
    call('DELETE', f'/projects/{project}/documents/{text["id"]}', et, expected=403)
    call('DELETE', f'/projects/{project}/members/{engineer["user"]["id"]}', mt, expected=204)
    call('GET', f'/projects/{project}/documents/{doc["id"]}/download', et, expected=404)
    call('DELETE', f'/projects/{project}/documents/{doc["id"]}', mt, expected=204)
    no_private_file(project, doc['id'])
finally:
    call('DELETE', f'/projects/{project}', mt, expected=204)
    if other:
        call('DELETE', f'/projects/{other}', ot, expected=204)
for document in tracked:
    no_private_file(project, document)
print('Phase 3 live smoke passed: real PDF/TXT extraction, page metadata, private byte-identical downloads, isolation/mismatched IDs, viewer/upload/delete rules, unsafe/oversized rejection, visible scanned-PDF failure/retry, revocation and file cleanup.')
