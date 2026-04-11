.PHONY: help test test-core test-workspace-core compile smoke smoke-workspace \
        infra-up infra-down infra-logs run

WORKSPACE_CORE_MODULE ?= services/workspace-core
WORKSPACE_CORE_POM ?= $(WORKSPACE_CORE_MODULE)/pom.xml
SMOKE_SCRIPT ?= ./infra/scripts/smoke-thin-slice.sh
MEDIA_FILE ?= /Users/nqd2005/Projects/DemoFirstBackend/IELTS Listening Practice Test 2025 With Answers 28.07.2025 - Test \#32 [YDBv6DR3BI8].mp4
SEARCH_QUERY ?=
UPLOAD_TITLE ?=
WORKSPACE_NAME ?= Demo Workspace
SMOKE_VERIFY_CONTEXT ?= 1
SMOKE_CONTEXT_WINDOW ?= 2

ENV_FILE ?= .env
COMPOSE_FILE ?= infra/docker-compose.dev.yml

help:
	@printf '%s\n' \
		'Available targets:' \
		'  make infra-up            Start Repo B PostgreSQL + Elasticsearch with docker compose' \
		'  make infra-down          Stop Repo B infrastructure' \
		'  make infra-logs          Show Repo B infrastructure logs' \
		'  make run                 Run workspace-core with env loaded from .env' \
		'  make test                Run workspace-core tests' \
		'  make compile             Compile workspace-core' \
		'  make smoke               Run the smoke helper against the default workspace' \
		'  make smoke-workspace     Run the smoke helper with a created non-default workspace' \
		'' \
		'Useful overrides:' \
		'  ENV_FILE=.env' \
		'  MEDIA_FILE=/absolute/path/to/media.mp4' \
		'  SEARCH_QUERY="binary search tree"' \
		'  UPLOAD_TITLE="Lecture 7"' \
		'  WORKSPACE_NAME="Algorithms"' \
		'  SMOKE_VERIFY_CONTEXT=1' \
		'  SMOKE_CONTEXT_WINDOW=2'

infra-up:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" up -d

infra-down:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" down

infra-logs:
	docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" logs -f

run:
	set -a && . "$(ENV_FILE)" && set +a && cd "$(WORKSPACE_CORE_MODULE)" && mvn spring-boot:run

test:
	mvn -q -f "$(WORKSPACE_CORE_POM)" test

compile:
	mvn -q -f "$(WORKSPACE_CORE_POM)" compile

smoke:
	SMOKE_VERIFY_CONTEXT="$(SMOKE_VERIFY_CONTEXT)" \
	SMOKE_CONTEXT_WINDOW="$(SMOKE_CONTEXT_WINDOW)" \
	"$(SMOKE_SCRIPT)" "$(MEDIA_FILE)" "$(SEARCH_QUERY)" "$(UPLOAD_TITLE)"

smoke-workspace:
	SMOKE_WORKSPACE_NAME="$(WORKSPACE_NAME)" \
	SMOKE_VERIFY_CONTEXT="$(SMOKE_VERIFY_CONTEXT)" \
	SMOKE_CONTEXT_WINDOW="$(SMOKE_CONTEXT_WINDOW)" \
	"$(SMOKE_SCRIPT)" "$(MEDIA_FILE)" "$(SEARCH_QUERY)" "$(UPLOAD_TITLE)"