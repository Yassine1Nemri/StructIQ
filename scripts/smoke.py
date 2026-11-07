"""Exercise the running core stack. Run after make up."""
import json
import subprocess
import urllib.error
import urllib.request


def read(url):
    with urllib.request.urlopen(url, timeout=10) as response:
        return response.read().decode()


assert "StructIQ" in read("http://localhost:5173/")
assert json.loads(read("http://localhost:5173/api/system/status"))["phase"] == "SECURE_RAG"
health = json.loads(read("http://localhost:8080/actuator/health"))
assert health["status"] == "UP"
assert set(health) <= {"status", "groups"}, "Health must not expose component details"
assert json.loads(read("http://localhost:8080/actuator/health/readiness"))["status"] == "UP"
try:
    read("http://localhost:8080/api/projects")
    raise AssertionError("Project routes must fail closed")
except urllib.error.HTTPError as error:
    assert error.code == 401
ai = subprocess.check_output(["docker", "compose", "exec", "-T", "ai", "python", "-c",
    "import urllib.request; print(urllib.request.urlopen('http://localhost:8000/health').read().decode())"], text=True)
assert json.loads(ai) == {"status": "UP"}
vector = subprocess.check_output(["docker", "compose", "exec", "-T", "postgres", "sh", "-c",
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "SELECT extversion FROM pg_extension WHERE extname=\'vector\'"'], text=True)
assert vector.strip(), "pgvector must be installed"
print("Smoke passed: web, proxied API, database readiness, private AI health, pgvector " + vector.strip() + ", protected route 401.")
