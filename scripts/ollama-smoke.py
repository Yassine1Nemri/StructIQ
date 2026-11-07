"""Exercise real local Qwen RAG through nginx/Spring/private FastAPI/pgvector.

Default removes its synthetic project. --keep-demo leaves a named demo to explore.
No credentials, tokens or private document contents are printed.
"""
import argparse
import json
from pathlib import Path
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--keep-demo', action='store_true')
args = parser.parse_args()
password = json.loads((Path(__file__).resolve().parents[1] / '.demo-credentials.json').read_text())['password']
base = 'http://localhost:5173/api'


def call(method, path, token=None, payload=None, expected=200, file=None):
    headers, data = {}, None
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if file:
        boundary = 'structiq-local-qwen-fixture'
        headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
        data = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="fictional-qwen-meeting.txt"\r\n'
                'Content-Type: text/plain\r\n\r\n' + file + f'\r\n--{boundary}--\r\n').encode()
    elif payload is not None:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(payload).encode()
    request = urllib.request.Request(base + path, method=method, headers=headers, data=data)
    try:
        with urllib.request.urlopen(request, timeout=165) as response:
            code, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, raw = error.code, error.read()
    assert code == expected, f'{method} {path}: expected {expected}, got {code}'
    return json.loads(raw) if raw and not path.endswith('/download') else raw


def login(name):
    return call('POST', '/auth/login', payload={'email': name + '@demo.local', 'password': password})


manager, outsider, engineer = (login(name) for name in ('manager', 'outsider', 'engineer'))
mt, ot, et = (session['accessToken'] for session in (manager, outsider, engineer))
project = call('POST', '/projects', mt, {'name': 'Local Qwen RAG demo', 'status': 'ACTIVE',
    'description': 'Synthetic document and real answers from local Ollama Qwen models.'}, 201)['id']
success = False
try:
    assert call('GET', f'/projects/{project}/ai/status', mt)['configured']
    text = ('FICTIONAL PROJECT MEETING NOTES\n'
            'Maya owns the safety review. The safety review is due on 12 November 2026. '
            'Omar owns the site inspection, due on 15 November 2026. '
            'The next meeting is on 18 November 2026. All names and events are synthetic.')
    doc = call('POST', f'/projects/{project}/documents', mt, expected=201, file=text)
    assert doc['processingStatus'] == 'READY', f'Indexing failed: {doc["processingCode"]}'
    print('Real Ollama upload/index passed: READY.', flush=True)
    call('POST', f'/projects/{project}/ai/ask', ot, {'question': 'Who owns safety review?'}, 404)
    call('GET', f'/projects/{project}/documents/{doc["id"]}/download', ot, expected=404)
    call('POST', f'/projects/{project}/members', mt, {'email': 'engineer@demo.local', 'role': 'VIEWER'}, 201)
    call('POST', f'/projects/{project}/documents/{doc["id"]}/index', et, expected=403)
    result = call('POST', f'/projects/{project}/ai/ask', mt,
                  {'question': 'Who owns the safety review, and when is it due?'})
    assert 'maya' in result['answer'].lower(), 'Real model did not answer the synthetic ownership question'
    assert result['citations'] and result['retrievalCount'] > 0, 'Expected grounded citations'
    for citation in result['citations']:
        assert citation['documentId'] == doc['id'] and citation['pageNumber'] == 1
        assert citation['excerpt'] in ' '.join(text.split())
    assert call('GET', f'/projects/{project}/documents/{doc["id"]}/download', mt) == text.encode()
    call('DELETE', f'/projects/{project}/members/{engineer["user"]["id"]}', mt, expected=204)
    call('POST', f'/projects/{project}/ai/ask', et, {'question': 'Who owns safety review?'}, 404)
    print('Real Qwen answer:', result['answer'], flush=True)
    print('Ollama smoke passed: live embeddings/generation, scoped citations/download, outsider and revoked-member denial, viewer index denial.', flush=True)
    success = True
finally:
    if not (success and args.keep_demo):
        call('DELETE', f'/projects/{project}', mt, expected=204)
    else:
        print('Kept synthetic project: Local Qwen RAG demo', flush=True)
