"""Assert Phase 0 network and secret boundaries against resolved Compose config."""
import json
import subprocess

config = json.loads(subprocess.check_output(["docker", "compose", "config", "--format", "json"], text=True))
services = config["services"]
assert set(services) == {"postgres", "backend", "ai", "frontend"}
for name in ("postgres", "ai"):
    assert not services[name].get("ports"), f"{name} must not expose host ports"
    assert set(services[name]["networks"]) == {"internal"}
assert config["networks"]["internal"]["internal"] is True
for name in ("backend", "frontend"):
    assert all(port["host_ip"] == "127.0.0.1" for port in services[name]["ports"])
for service in services.values():
    assert service.get("healthcheck"), "Every core service needs a healthcheck"
assert "GEMINI_API_KEY" not in services["frontend"].get("environment", {})
for name in ("backend","ai"):
    assert "INTERNAL_AI_SHARED_SECRET" in services[name]["environment"]
ai_mounts=services["ai"].get("volumes",[])
assert any(m.get("source")=="private_documents" and m.get("read_only") for m in ai_mounts)
assert not services["frontend"].get("volumes"), "Web server must not mount private uploads"
print("Compose boundary checks passed: four healthchecks, private AI/DB, localhost-only public ports.")
