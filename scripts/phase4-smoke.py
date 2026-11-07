"""Check default no-key behavior without issuing model/provider calls."""
import json
from pathlib import Path
import urllib.request
import urllib.error
import time

password = json.loads((Path(__file__).resolve().parents[1] / '.demo-credentials.json').read_text())['password']
base = 'http://localhost:5173/api'


def call(method, path, token=None, data=None, expected=200, retried=False):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(base+path, method=method, headers=headers, data=json.dumps(data).encode() if data else None)
    try:
        with urllib.request.urlopen(request, timeout=10) as response: code, raw = response.status, response.read()
    except urllib.error.HTTPError as error: code, raw = error.code, error.read()
    if code == 429 and path == '/auth/login' and not retried:
        print('Login limit reached by earlier smoke checks; waiting for the next window without weakening it.')
        time.sleep(min(60, 60 - time.time() % 60 + .1))
        return call(method, path, token, data, expected, True)
    assert code == expected, f'{method} {path}: {code} != {expected}'
    return json.loads(raw)


token = call('POST', '/auth/login', data={'email': 'manager@demo.local', 'password': password})['accessToken']
project = call('GET', '/projects', token)['items'][0]['id']
status = call('GET', f'/projects/{project}/ai/status', token)
if status['configured']:
    print('AI is configured; no model question was sent. Use make smoke-ollama for live local Qwen or make proof-rag for deterministic provider-mocked proof.')
else:
    response = call('POST', f'/projects/{project}/ai/ask', token, {'question': 'Who owns safety review?'}, 503)
    assert response['code'] == 'AI_NOT_CONFIGURED'
    assert call('GET', '/dashboard/summary', token)['totalProjects'] > 0
    print('Phase 4 no-key smoke passed: explicit AI_NOT_CONFIGURED 503 and working project/dashboard flows; no provider call.')
