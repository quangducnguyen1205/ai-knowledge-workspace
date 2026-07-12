.PHONY: help test test-core test-workspace-core compile smoke smoke-workspace \
        infra-up infra-down infra-logs kafka-config-check run run-project3 run-compatibility run-standalone require-media-file

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
SMOKE_USE_LEGACY_AUTH_FALLBACK ?=
SMOKE_LEGACY_USER_ID ?= smoke-dev-user

ENV_FILE ?= .env
COMPOSE_FILE ?= infra/docker-compose.dev.yml
KAFKA_CONFIG_VALIDATOR ?= infra/scripts/validate-kafka-runtime-config.py
SPRING_PROFILE ?= project3

help:
	@printf '%s\n' \
		'Available targets:' \
		'  make infra-up            Start Repo B PostgreSQL + Elasticsearch with docker compose' \
		'  make infra-down          Stop Repo B infrastructure' \
		'  make infra-logs          Show Repo B infrastructure logs' \
		'  make kafka-config-check  Statically validate rendered local Kafka resource/restart settings' \
		'  make run                 Run workspace-core with the coherent project3 profile' \
		'  make run-project3        Compatibility alias for the normal integrated run target' \
		'  make run-compatibility   Deprecated but functional direct_upload rollback path' \
		'  make run-standalone      Deprecated alias for run-compatibility' \
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
		'  SMOKE_USE_LEGACY_AUTH_FALLBACK=1' \
		'  SMOKE_LEGACY_USER_ID="smoke-dev-user"' \
		'  SMOKE_VERIFY_CONTEXT=1' \
		'  SMOKE_CONTEXT_WINDOW=2'

infra-up:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" up -d

infra-down:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" down

infra-logs:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" logs -f

kafka-config-check:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" config --format json | python3 "$(KAFKA_CONFIG_VALIDATOR)"

run:
	set -a && . "$(ENV_FILE)" && set +a && cd "$(WORKSPACE_CORE_MODULE)" && mvn spring-boot:run -Dspring-boot.run.profiles="$(SPRING_PROFILE)"

run-project3:
	$(MAKE) run SPRING_PROFILE=project3

run-compatibility:
	@printf '%s\n' 'DEPRECATION: direct_upload is retained temporarily for rollback and recovery; use make run for normal Project3 operation. No removal date is assigned.'
	$(MAKE) run SPRING_PROFILE=compatibility

run-standalone:
	@printf '%s\n' 'DEPRECATION: make run-standalone is a deprecated alias; use make run-compatibility for rollback and make run for normal operation.'
	$(MAKE) run-compatibility

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
	SMOKE_USE_LEGACY_AUTH_FALLBACK="$(SMOKE_USE_LEGACY_AUTH_FALLBACK)" \
	SMOKE_LEGACY_USER_ID="$(SMOKE_LEGACY_USER_ID)" \
	SMOKE_VERIFY_CONTEXT="$(SMOKE_VERIFY_CONTEXT)" \
	SMOKE_CONTEXT_WINDOW="$(SMOKE_CONTEXT_WINDOW)" \
	"$(SMOKE_SCRIPT)" "$(MEDIA_FILE)" "$(SEARCH_QUERY)" "$(UPLOAD_TITLE)"

smoke-workspace: require-media-file
	SMOKE_WORKSPACE_NAME="$(WORKSPACE_NAME)" \
	SMOKE_AUTH_EMAIL="$(SMOKE_AUTH_EMAIL)" \
	SMOKE_AUTH_PASSWORD="$(SMOKE_AUTH_PASSWORD)" \
	SMOKE_USE_LEGACY_AUTH_FALLBACK="$(SMOKE_USE_LEGACY_AUTH_FALLBACK)" \
	SMOKE_LEGACY_USER_ID="$(SMOKE_LEGACY_USER_ID)" \
	SMOKE_VERIFY_CONTEXT="$(SMOKE_VERIFY_CONTEXT)" \
	SMOKE_CONTEXT_WINDOW="$(SMOKE_CONTEXT_WINDOW)" \
	"$(SMOKE_SCRIPT)" "$(MEDIA_FILE)" "$(SEARCH_QUERY)" "$(UPLOAD_TITLE)"
