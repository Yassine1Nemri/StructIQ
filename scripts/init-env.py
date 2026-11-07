"""Create a private local .env without overwriting existing values."""
from pathlib import Path
import os
import secrets

path = Path(__file__).resolve().parents[1] / ".env"
if path.exists():
    text = path.read_text()
else:
    text = path.with_name(".env.example").read_text()
for placeholder in (
    "replace-with-generated-password",
    "replace-with-generated-jwt-secret",
    "replace-with-generated-internal-secret",
):
    text = text.replace(placeholder, secrets.token_urlsafe(48))
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w") as stream:
    stream.write(text)
path.chmod(0o600)
print("Local .env prepared (mode 0600); values were not printed.")
