COMPOSE = docker compose
.PHONY: up down logs test config smoke smoke-phase1 smoke-phase2 smoke-phase3 smoke-phase4 smoke-ollama proof-rag secrets seed showcase clean
up:
	$(COMPOSE) up --build --wait --wait-timeout 240

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f --tail=100

test:
	docker build --target test -t structiq-backend-test backend
	docker build --target test -t structiq-frontend-test frontend
	docker build --target test -t structiq-ai-test ai-service
	python3 scripts/check-compose.py

config:
	$(COMPOSE) config --quiet

smoke:
	python3 scripts/smoke.py

smoke-phase1:
	python3 scripts/phase1-smoke.py

smoke-phase2:
	python3 scripts/phase2-smoke.py

smoke-phase3:
	python3 scripts/phase3-smoke.py

smoke-phase4:
	python3 scripts/phase4-smoke.py

smoke-ollama:
	python3 scripts/ollama-smoke.py

proof-rag:
	python3 scripts/rag-proof.py

secrets:
	python3 scripts/init-env.py

seed:
	python3 scripts/seed-demo-data.py

showcase:
	python3 scripts/seed-showcase.py

clean:
	@test "$(CONFIRM)" = "DELETE" || (echo "WARNING: removes database and private document volumes. Use make clean CONFIRM=DELETE"; exit 1)
	$(COMPOSE) down --volumes --remove-orphans
