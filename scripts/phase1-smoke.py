"""Live PostgreSQL-backed API proof using synthetic accounts. Run make seed first."""
import json
from pathlib import Path
import urllib.error
import urllib.request

password = json.loads((Path(__file__).resolve().parents[1] / ".demo-credentials.json").read_text())["password"]
base = "http://localhost:5173/api"


def call(method, path, token=None, payload=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(base + path, method=method, headers=headers,
                                     data=None if payload is None else json.dumps(payload).encode())
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            code, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, raw = error.code, error.read()
    assert code == expected, f"{method} {path}: expected {expected}, got {code}"
    return json.loads(raw) if raw else None


def login(name):
    return call("POST", "/auth/login", payload={"email": name + "@demo.local", "password": password})


manager, engineer, outsider, admin = (login(name) for name in ("manager", "engineer", "outsider", "admin"))
mt, et, ot, at = (session["accessToken"] for session in (manager, engineer, outsider, admin))
assert call("GET", "/auth/me", mt)["email"] == "manager@demo.local"
call("GET", "/projects", expected=401)
created = call("POST", "/projects", mt, {"name": "Phase 1 verification site", "status": "PLANNED"}, 201)
project = created["id"]
try:
    assert created["role"] == "MANAGER"
    call("GET", f"/projects/{project}", ot, expected=404)
    call("GET", f"/projects/{project}/members", ot, expected=404)
    call("PATCH", f"/projects/{project}", ot, {"name": "Forbidden"}, 404)
    call("DELETE", f"/projects/{project}", ot, expected=404)
    assert call("GET", f"/projects/{project}", at)["role"] == "ADMIN"
    call("POST", f"/projects/{project}/members", mt, {"email": "engineer@demo.local", "role": "MEMBER"}, 201)
    assert call("GET", f"/projects/{project}", et)["role"] == "MEMBER"
    member_path = f"/projects/{project}/members/{engineer['user']['id']}"
    call("PATCH", member_path, et, {"role": "MANAGER"}, 403)
    call("POST", "/projects", et, {"name": "Forbidden", "status": "ACTIVE"}, 403)
    call("PATCH", f"/projects/{project}", mt, {"status": "ARCHIVED"})
    found = call("GET", "/projects?q=Phase%201%20verification&status=ARCHIVED", mt)
    assert project in {item["id"] for item in found["items"]}
    call("PATCH", member_path, mt, {"role": "VIEWER"}, 204)
    call("PATCH", f"/projects/{project}", et, {"name": "Forbidden"}, 403)
    call("DELETE", member_path, mt, expected=204)
    call("GET", f"/projects/{project}", et, expected=404)
    call("DELETE", f"/projects/{project}/members/{manager['user']['id']}", mt, expected=409)
finally:
    call("DELETE", f"/projects/{project}", mt, expected=204)
call("GET", f"/projects/{project}", mt, expected=404)
print("Phase 1 live smoke passed: login, current user, project CRUD/filtering, admin access, two-user isolation, member/viewer restrictions, revocation, last-manager protection, and cleanup.")
