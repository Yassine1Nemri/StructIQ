"""Add a synthetic local showcase through the same authorized APIs as the UI.

Creates only its own named project/documents/tasks, preserves existing records,
indexes using the selected real provider and verifies a grounded blocker answer.
Requires the normal seeded accounts and configured AI. No secrets are printed.
"""
import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
import urllib.error
import urllib.request

BASE = 'http://localhost:5173/api'
NAME = 'Riverside Delivery Lab'
DESCRIPTION = 'A fictional community workspace renovation. Explore the delivery board, review coordination notes, and ask the project assistant for a grounded briefing. All people, decisions, and events are synthetic.'
password = json.loads((Path(__file__).resolve().parents[1] / '.demo-credentials.json').read_text())['password']


def call(method, path, token=None, payload=None, file=None, expected=200):
    headers, body = {}, None
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if file:
        filename, content = file
        boundary = 'structiq-showcase-document'
        headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                'Content-Type: text/plain\r\n\r\n' + content + f'\r\n--{boundary}--\r\n').encode()
    elif payload is not None:
        headers['Content-Type'] = 'application/json'
        body = json.dumps(payload).encode()
    request = urllib.request.Request(BASE + path, method=method, headers=headers, data=body)
    try:
        with urllib.request.urlopen(request, timeout=165) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    assert status == expected, f'{method} {path}: expected {expected}, got {status}'
    return json.loads(raw) if raw else None


session = call('POST', '/auth/login', payload={'email': 'manager@demo.local', 'password': password})
token, manager = session['accessToken'], session['user']['id']
projects = call('GET', '/projects?q=Riverside%20Delivery%20Lab', token)['items']
project = next((p for p in projects if p['name'] == NAME and p['description'] == DESCRIPTION), None)
today = datetime.now(timezone.utc).date()
if project is None:
    project = call('POST', '/projects', token, {'name': NAME, 'description': DESCRIPTION, 'status': 'ACTIVE',
        'startDate': str(today - timedelta(days=14)), 'targetDate': str(today + timedelta(days=40))}, expected=201)
project_id = project['id']
base_date = date.fromisoformat(project['startDate']) + timedelta(days=14)
def day(offset):
    return (base_date + timedelta(days=offset)).strftime('%d %B %Y')

members = call('GET', f'/projects/{project_id}/members', token)
engineer = next((m for m in members if m['email'] == 'engineer@demo.local'), None)
if engineer is None:
    engineer = call('POST', f'/projects/{project_id}/members', token, {'email': 'engineer@demo.local', 'role': 'MEMBER'}, expected=201)
assert call('GET', f'/projects/{project_id}/ai/status', token)['configured'], 'Configure the real AI provider first'

documents = [
    ('01-coordination-meeting.txt',
     f'FICTIONAL RIVERSIDE DELIVERY LAB - COORDINATION MEETING, {day(-1)}. '
     'The community workspace renovation is in the delivery stage. '
     'BLOCKER: the fire-door installation cannot begin until the supplier confirms the revised delivery date. '
     f'Demo manager owns supplier follow-up, due {day(2)}. '
     f'Demo engineer owns the safety review, due {day(5)}. '
     f'The next coordination meeting is {day(7)}. '
     'The team agreed that no fire-door installation may start before supplier confirmation and the safety review. '
     'All people and events in this demonstration are synthetic.'),
    ('02-design-decision-record.txt',
     f'FICTIONAL RIVERSIDE DELIVERY LAB - DESIGN DECISIONS, {day(-2)}. '
     'DECISION D-01: retain the existing concrete stair and repair the handrail; full stair replacement is rejected. '
     'DECISION D-02: use warm-white LED lighting in the shared studio. '
     'DECISION D-03: preserve the original brick wall in the entrance; the client approved this at the design review. '
     f'Demo engineer owns the revised lighting layout, due {day(3)}. '
     'The accessibility route must remain unobstructed throughout renovation. '
     'The unresolved issue is acoustic panel placement, pending a client decision. '
     'All details are synthetic demonstration data.'),
    ('03-delivery-and-risk-register.txt',
     f'FICTIONAL RIVERSIDE DELIVERY LAB - DELIVERY AND RISK REGISTER, {day(0)}. '
     'RISK R-01: late fire-door delivery can delay installation. Demo manager is the risk owner. '
     'Mitigation: request a written revised delivery date and assess a backup supplier. '
     f'RISK R-02: acoustic panel approval is still pending. Demo manager must obtain a client decision by {day(4)}. '
     f'Demo engineer owns the site inspection, due {day(1)}. '
     'Completed milestones: initial site survey and concept approval. '
     f'Planned handover target: {day(40)}. '
     'There is no approved budget figure in these documents. Never infer one. '
     'All details are synthetic demonstration data.'),
]
existing = call('GET', f'/projects/{project_id}/documents', token)['items']
for filename, text in documents:
    document = next((d for d in existing if d['filename'] == filename), None)
    if document is None:
        document = call('POST', f'/projects/{project_id}/documents', token, file=(filename, text), expected=201)
    if document['processingStatus'] != 'READY':
        call('POST', f'/projects/{project_id}/documents/{document["id"]}/index', token)
    print('Indexed synthetic source:', filename, flush=True)

tasks = [
    ('Confirm fire-door delivery', 'Request written confirmation from the supplier. Installation is blocked until confirmation and safety review.', 'BLOCKED', 'HIGH', manager, 2),
    ('Complete safety review', 'Check the fire-door installation sequence and site safety requirements.', 'IN_PROGRESS', 'HIGH', engineer['userId'], 5),
    ('Revise studio lighting layout', 'Implement decision D-02: warm-white LED lighting in the shared studio.', 'IN_PROGRESS', 'MEDIUM', engineer['userId'], 3),
    ('Finish site inspection', 'Record site observations and update the coordination team.', 'TODO', 'HIGH', engineer['userId'], 1),
    ('Resolve acoustic panel placement', 'Obtain the client decision and record the approved location.', 'TODO', 'MEDIUM', manager, 4),
    ('Publish coordination minutes', 'Circulate the synthetic coordination record and confirm owners.', 'TODO', 'LOW', manager, -2),
    ('Initial site survey', 'Existing structure surveyed and documented.', 'DONE', 'MEDIUM', engineer['userId'], -10),
    ('Approve design concept', 'Concept approved, including preservation of the entrance brick wall.', 'DONE', 'MEDIUM', manager, -7),
]
existing_tasks = call('GET', f'/projects/{project_id}/tasks?size=100', token)['items']
for title, description, status, priority, assignee, offset in tasks:
    if not any(t['title'] == title for t in existing_tasks):
        call('POST', f'/projects/{project_id}/tasks', token, {'title': title, 'description': description,
            'status': status, 'priority': priority, 'assigneeId': assignee, 'dueDate': str(base_date + timedelta(days=offset))}, expected=201)
print('Showcase ready: three indexed sources and eight real tasks.', flush=True)
result = call('POST', f'/projects/{project_id}/ai/ask', token,
              {'question': 'What is blocking the fire-door installation, who owns the supplier follow-up, and what must happen before installation starts?'})
assert result['citations'], 'Expected source citations from the real provider'
assert all(c['documentId'] in {d['id'] for d in call('GET', f'/projects/{project_id}/documents', token)['items']} for c in result['citations'])
assert 'supplier' in result['answer'].lower(), 'Expected the recorded supplier blocker'
print('Real local answer:', result['answer'], flush=True)
print('Open: http://localhost:5173/projects/' + project_id + '?tab=assistant', flush=True)
