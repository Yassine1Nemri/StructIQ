"""Run the opt-in Java seed against the running backend's database."""
import json
import os
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parents[1]
password = secrets.token_urlsafe(24)
credential_file = root / ".demo-credentials.json"
# Keep the credential stable across retries; never print it or pass it as a CLI argument.
if credential_file.exists():
    password = json.loads(credential_file.read_text())["password"]
else:
    fd = os.open(credential_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump({"password": password, "emails": [f"{u}@demo.local" for u in
                  ("admin", "manager", "engineer", "outsider")]}, stream, indent=2)
credential_file.chmod(0o600)
subprocess.run([
    "docker", "compose", "exec", "-T", "backend", "sh", "-c",
    'read -r DEMO_PASSWORD; export DEMO_PASSWORD; exec java -jar /app/app.jar '
    '--server.port=0 --structiq.demo-seed-enabled=true',
], input=password + "\n", text=True, check=True, cwd=root)
print("Synthetic accounts ready. Read .demo-credentials.json locally for credentials (mode 0600).")
