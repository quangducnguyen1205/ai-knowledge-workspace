.PHONY: help test test-core test-workspace-core compile smoke smoke-workspace \
        infra-up infra-down infra-logs infra-wait kafka-config-check run run-project3 \
        require-media-file build-identity stack-up stack-down

WORKSPACE_CORE_MODULE ?= services/workspace-core
WORKSPACE_CORE_POM ?= $(WORKSPACE_CORE_MODULE)/pom.xml
SMOKE_SCRIPT ?= ./infra/scripts/smoke-thin-slice.sh
MEDIA_FILE ?=
SEARCH_QUERY ?=
UPLOAD_TITLE ?=
WORKSPACE_NAME ?= Demo Workspace
SMOKE_VERIFY_CONTEXT ?= 1
SMOKE_CONTEXT_WINDOW ?= 2
SMOKE_AUTH_EMAIL ?=
SMOKE_AUTH_PASSWORD ?=

ENV_FILE ?= .env
COMPOSE_FILE ?= infra/docker-compose.dev.yml
KAFKA_CONFIG_VALIDATOR ?= infra/scripts/validate-kafka-runtime-config.py
SPRING_PROFILE ?= project3

help:
	@printf '%s\n' \
		'Available targets:' \
		'  make stack-up            Start the full local dependency stack and wait for readiness' \
		'  make stack-down          Stop the stack without removing any volume' \
		'  make build-identity      Package workspace-core with the current git revision stamped in' \
		'  make infra-up            Start Repo B PostgreSQL + Elasticsearch with docker compose' \
		'  make infra-wait          Block until every dependency health check reports healthy' \
		'  make infra-down          Stop Repo B infrastructure' \
		'  make infra-logs          Show Repo B infrastructure logs' \
		'  make kafka-config-check  Statically validate rendered local Kafka resource/restart settings' \
		'  make run                 Run workspace-core with the coherent project3 profile' \
		'  make run-project3        Alias for the normal integrated run target' \
		'  make test                Run workspace-core tests' \
		'  make test-core           Run workspace-core tests' \
		'  make test-workspace-core Run workspace-core tests' \
		'  make compile             Compile workspace-core' \
		'  make smoke               Run the smoke helper against the default workspace (requires MEDIA_FILE)' \
		'  make smoke-workspace     Run the smoke helper with a created non-default workspace (requires MEDIA_FILE)' \
		'' \
		'Useful overrides:' \
		'  ENV_FILE=.env' \
		'  SPRING_PROFILE=project3' \
		'  MEDIA_FILE=/absolute/path/to/media.mp4' \
		'  SEARCH_QUERY="binary search tree"' \
		'  UPLOAD_TITLE="Lecture 7"' \
		'  WORKSPACE_NAME="Algorithms"' \
		'  SMOKE_AUTH_EMAIL="smoke-user@example.com"  # optional on localhost, required for non-local targets' \
		'  SMOKE_AUTH_PASSWORD="password123"          # optional on localhost, required for non-local targets' \
		'  SMOKE_VERIFY_CONTEXT=1' \
		'  SMOKE_CONTEXT_WINDOW=2'

infra-up:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" up -d

# Blocks until every long-running dependency reports healthy, so the next step never starts
# against a socket that is open but not ready. Readiness comes from the Compose health checks:
# there is no blind fixed startup delay; health is polled every two seconds and the wait ends as
# soon as every service reports healthy.
infra-wait:
	@printf 'Waiting for infrastructure readiness'
	@for i in $$(seq 1 90); do \
		unhealthy=$$(docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" ps \
			--format '{{.Service}} {{.Health}}' \
			| awk '$$2 != "healthy" && $$2 != "" { print $$1 }'); \
		if [ -z "$$unhealthy" ]; then printf ' ready\n'; exit 0; fi; \
		printf '.'; sleep 2; \
	done; \
	printf '\nStill not ready: %s\n' "$$unhealthy" >&2; \
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" ps >&2; \
	exit 1

# One documented command for the complete local dependency stack.
stack-up: infra-up infra-wait
	@echo 'Infrastructure ready. Start the product core with: make run'

# Clean shutdown. Containers stop; named volumes are never removed by this target.
stack-down:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" stop

# Builds the product core with the running revision stamped into build-info.properties.
build-identity:
	mvn -f "$(WORKSPACE_CORE_POM)" -DskipTests -Dbuild.git.commit="$(GIT_COMMIT)" package

infra-down:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" down

infra-logs:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" logs -f

kafka-config-check:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" config --format json | python3 "$(KAFKA_CONFIG_VALIDATOR)"

# GIT_COMMIT is stamped into build-info.properties so /api/build-info can always report which
# revision is running, including for a local development launch.
GIT_COMMIT ?= $(shell git rev-parse HEAD 2>/dev/null || echo unknown)

run:
	set -a && . "$(ENV_FILE)" && set +a && cd "$(WORKSPACE_CORE_MODULE)" && mvn spring-boot:run -Dbuild.git.commit="$(GIT_COMMIT)" -Dspring-boot.run.profiles="$(SPRING_PROFILE)"

run-project3:
	$(MAKE) run SPRING_PROFILE=project3

test:
	mvn -q -f "$(WORKSPACE_CORE_POM)" test

test-core: test

test-workspace-core: test

compile:
	mvn -q -f "$(WORKSPACE_CORE_POM)" compile

require-media-file:
	@test -n "$(strip $(MEDIA_FILE))" || ( \
		echo 'MEDIA_FILE is required. Example: make smoke MEDIA_FILE=/absolute/path/to/media.mp4' >&2; \
		exit 1; \
	)

smoke: require-media-file
	SMOKE_AUTH_EMAIL="$(SMOKE_AUTH_EMAIL)" \
	SMOKE_AUTH_PASSWORD="$(SMOKE_AUTH_PASSWORD)" \
	SMOKE_VERIFY_CONTEXT="$(SMOKE_VERIFY_CONTEXT)" \
	SMOKE_CONTEXT_WINDOW="$(SMOKE_CONTEXT_WINDOW)" \
	"$(SMOKE_SCRIPT)" "$(MEDIA_FILE)" "$(SEARCH_QUERY)" "$(UPLOAD_TITLE)"

smoke-workspace: require-media-file
	SMOKE_WORKSPACE_NAME="$(WORKSPACE_NAME)" \
	SMOKE_AUTH_EMAIL="$(SMOKE_AUTH_EMAIL)" \
	SMOKE_AUTH_PASSWORD="$(SMOKE_AUTH_PASSWORD)" \
	SMOKE_VERIFY_CONTEXT="$(SMOKE_VERIFY_CONTEXT)" \
	SMOKE_CONTEXT_WINDOW="$(SMOKE_CONTEXT_WINDOW)" \
	"$(SMOKE_SCRIPT)" "$(MEDIA_FILE)" "$(SEARCH_QUERY)" "$(UPLOAD_TITLE)"
