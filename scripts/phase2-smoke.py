"""Live Phase 2 proof through nginx and PostgreSQL; only disposable synthetic projects are changed."""
from datetime import timedelta, timezone, datetime
import json
from pathlib import Path
import urllib.error
import urllib.request

password = json.loads((Path(__file__).resolve().parents[1] / '.demo-credentials.json').read_text())['password']
base = 'http://localhost:5173/api'


def call(method, path, token=None, payload=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(base + path, method=method, headers=headers,
                                     data=None if payload is None else json.dumps(payload).encode())
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            code, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, raw = error.code, error.read()
    assert code == expected, f'{method} {path}: expected {expected}, got {code}'
    return json.loads(raw) if raw else None


def login(name):
    return call('POST', '/auth/login', payload={'email': name + '@demo.local', 'password': password})


manager, engineer, outsider = (login(name) for name in ('manager', 'engineer', 'outsider'))
mt, et, ot = (s['accessToken'] for s in (manager, engineer, outsider))
initial = call('GET', '/dashboard/summary', mt)
project = call('POST', '/projects', mt, {'name': 'Phase 2 verification site', 'status': 'ACTIVE'}, 201)['id']
other = None
try:
    other = call('POST', '/projects', ot, {'name': 'Hidden Phase 2 site', 'status': 'ACTIVE'}, 201)['id']
    call('POST', f'/projects/{project}/members', mt, {'email': 'engineer@demo.local', 'role': 'MEMBER'}, 201)
    yesterday = (datetime.now(timezone.utc).date() - timedelta(days=1)).isoformat()
    task_input = {'title': 'Verify task persistence', 'description': 'Synthetic verification only.', 'status': 'TODO',
                  'priority': 'HIGH', 'assigneeId': engineer['user']['id'], 'dueDate': yesterday}
    task = call('POST', f'/projects/{project}/tasks', mt, task_input, 201)['id']
    hidden = call('POST', f'/projects/{other}/tasks', ot, {**task_input, 'title': 'Hidden task', 'assigneeId': outsider['user']['id']}, 201)['id']
    before = call('GET', '/dashboard/summary', mt)
    assert before['totalProjects'] == initial['totalProjects'] + 1
    assert before['openTasks'] == initial['openTasks'] + 1
    assert before['overdueTasks'] == initial['overdueTasks'] + 1
    assert other not in json.dumps(before)
    assert hidden not in json.dumps(before)
    call('GET', f'/projects/{other}/tasks', mt, expected=404)
    call('PATCH', f'/projects/{other}/tasks/{hidden}', mt, {'status': 'DONE'}, 404)
    call('PATCH', f'/projects/{project}/tasks/{hidden}', mt, {'status': 'DONE'}, 404)
    call('PATCH', f'/projects/{project}/tasks/{task}', mt, {'assigneeId': outsider['user']['id']}, 400)
    call('PATCH', f'/projects/{project}/tasks/{task}', et, {'status': 'DONE', 'title': 'Forbidden'}, 403)
    call('PATCH', f'/projects/{project}/tasks/{task}', et, {'status': 'DONE'})
    after = call('GET', '/dashboard/summary', mt)
    assert after['openTasks'] == initial['openTasks']
    assert after['overdueTasks'] == initial['overdueTasks']
    assert call('GET', f'/projects/{project}', mt)['progressPercent'] == 100
    own = call('GET', '/tasks/mine?status=DONE&size=100', et)
    assert task in {item['id'] for item in own['items']}
    assert call('GET', f'/projects/{project}/tasks?status=DONE&priority=HIGH&overdue=false', mt)['items'][0]['id'] == task
    call('PATCH', f'/projects/{project}/tasks/{task}', mt, {'dueDate': None, 'assigneeId': None})
    call('PATCH', f'/projects/{project}/tasks/{task}', et, {'status': 'TODO'}, 403)
    call('PATCH', f'/projects/{project}/tasks/{task}', mt, {'assigneeId': engineer['user']['id']})
    call('DELETE', f'/projects/{project}/members/{engineer["user"]["id"]}', mt, expected=204)
    assert call('GET', f'/projects/{project}/tasks', mt)['items'][0]['assigneeId'] is None
    call('GET', f'/projects/{project}/tasks', et, expected=404)
finally:
    call('DELETE', f'/projects/{project}', mt, expected=204)
    if other:
        call('DELETE', f'/projects/{other}', ot, expected=204)
assert call('GET', '/dashboard/summary', mt)['totalProjects'] == initial['totalProjects']
print('Phase 2 live smoke passed: task persistence, own-status policy, scoped assignment/IDs, filters, dashboard deltas, progress, optional-field clearing, removal unassignment, and cascade cleanup.')
