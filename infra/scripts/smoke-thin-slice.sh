#!/usr/bin/env bash

set -euo pipefail

WORKSPACE_CORE_BASE_URL="${WORKSPACE_CORE_BASE_URL:-http://localhost:8081}"
SMOKE_POLL_INTERVAL_SECONDS="${SMOKE_POLL_INTERVAL_SECONDS:-3}"
SMOKE_POLL_TIMEOUT_SECONDS="${SMOKE_POLL_TIMEOUT_SECONDS:-180}"
SMOKE_WORKSPACE_NAME="${SMOKE_WORKSPACE_NAME:-}"
SMOKE_VERIFY_CONTEXT="${SMOKE_VERIFY_CONTEXT:-}"
SMOKE_CONTEXT_WINDOW="${SMOKE_CONTEXT_WINDOW:-2}"
SMOKE_AUTH_EMAIL="${SMOKE_AUTH_EMAIL:-}"
SMOKE_AUTH_PASSWORD="${SMOKE_AUTH_PASSWORD:-}"
SMOKE_USE_LEGACY_AUTH_FALLBACK="${SMOKE_USE_LEGACY_AUTH_FALLBACK:-}"
SMOKE_LEGACY_USER_ID="${SMOKE_LEGACY_USER_ID:-smoke-dev-user}"

API_HTTP_CODE=""
API_BODY_FILE=""
TEMP_DIR=""
COOKIE_JAR=""
SMOKE_WORKSPACE_ID=""

usage() {
    cat <<'EOF'
Usage:
  ./infra/scripts/smoke-thin-slice.sh /path/to/media-file [search-query] [title]

Environment variables:
  WORKSPACE_CORE_BASE_URL       Default: http://localhost:8081
  SMOKE_POLL_INTERVAL_SECONDS   Default: 3
  SMOKE_POLL_TIMEOUT_SECONDS    Default: 180
  SMOKE_WORKSPACE_NAME          Optional: create and use a non-default workspace for this run
  SMOKE_VERIFY_CONTEXT          Optional: when set to 1/true/yes/on, fetch transcript context for the top search hit
  SMOKE_CONTEXT_WINDOW          Optional: transcript context window to use when SMOKE_VERIFY_CONTEXT is enabled (default: 2)
  SMOKE_AUTH_EMAIL              Optional: defaults to smoke-user@example.com on localhost only; required for non-local targets
  SMOKE_AUTH_PASSWORD           Optional: defaults to password123 on localhost only; required for non-local targets
  SMOKE_USE_LEGACY_AUTH_FALLBACK Optional: when set to 1/true/yes/on, skip register/login and use /api/auth/session instead
  SMOKE_LEGACY_USER_ID          Optional: userId to use with the legacy auth-session fallback (default: smoke-dev-user)

Notes:
  - Repo A, PostgreSQL, Elasticsearch, and workspace-core must already be running.
  - The helper now uses the authenticated product path by default:
    register/login -> /api/me -> workspace -> upload -> status -> transcript -> index -> search -> context.
  - If search-query is omitted, the script derives a simple query from the first transcript row.
  - If SMOKE_WORKSPACE_NAME is set, the script creates a workspace, reads it back, uploads into it,
    lists assets in it, and searches within it.
  - If SMOKE_VERIFY_CONTEXT is enabled, the script also opens the top search hit through
    /api/assets/{assetId}/transcript/context.
  - The older /api/auth/session path remains available only when SMOKE_USE_LEGACY_AUTH_FALLBACK is enabled.
EOF
}

cleanup() {
    if [[ -n "${TEMP_DIR}" && -d "${TEMP_DIR}" ]]; then
        rm -rf "${TEMP_DIR}"
    fi
}

fail() {
    echo
    echo "ERROR: $1" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

print_step() {
    echo
    echo "==> $1"
}

pretty_print_body() {
    local file_path="$1"
    if jq . "$file_path" >/dev/null 2>&1; then
        jq . "$file_path"
    else
        cat "$file_path"
    fi
}

is_local_base_url() {
    case "$WORKSPACE_CORE_BASE_URL" in
        http://localhost:*|http://127.0.0.1:*|http://[::1]:*|https://localhost:*|https://127.0.0.1:*|https://[::1]:*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

initialize_auth_defaults() {
    if is_truthy "$SMOKE_USE_LEGACY_AUTH_FALLBACK"; then
        return
    fi

    if is_local_base_url; then
        SMOKE_AUTH_EMAIL="${SMOKE_AUTH_EMAIL:-smoke-user@example.com}"
        SMOKE_AUTH_PASSWORD="${SMOKE_AUTH_PASSWORD:-password123}"
        return
    fi

    [[ -n "${SMOKE_AUTH_EMAIL// }" ]] || fail "SMOKE_AUTH_EMAIL is required when WORKSPACE_CORE_BASE_URL is not localhost"
    [[ -n "${SMOKE_AUTH_PASSWORD// }" ]] || fail "SMOKE_AUTH_PASSWORD is required when WORKSPACE_CORE_BASE_URL is not localhost"
}

fail_api() {
    local message="$1"
    local error_code=""
    echo
    echo "ERROR: $message" >&2
    echo "HTTP status: $API_HTTP_CODE" >&2

    if [[ -s "$API_BODY_FILE" ]]; then
        echo "Response body:" >&2
        pretty_print_body "$API_BODY_FILE" >&2
        error_code="$(jq -r '.code // empty' "$API_BODY_FILE" 2>/dev/null || true)"
    fi

    case "$error_code" in
        FASTAPI_CONNECTIVITY_ERROR)
            echo "Classification hint: likely upstream FastAPI readiness/connectivity issue." >&2
            ;;
        ELASTICSEARCH_UNAVAILABLE|ELASTICSEARCH_INTEGRATION_ERROR)
            echo "Classification hint: likely Elasticsearch readiness/integration issue." >&2
            ;;
        INVALID_CREDENTIALS|INVALID_AUTH_REQUEST|INVALID_EMAIL|INVALID_PASSWORD|AUTHENTICATION_REQUIRED)
            echo "Classification hint: auth/session setup failed before the product flow could run." >&2
            ;;
        "")
            if [[ "$API_HTTP_CODE" == "502" || "$API_HTTP_CODE" == "504" ]]; then
                echo "Classification hint: likely upstream dependency or integration issue rather than a frontend proxy problem." >&2
            fi
            ;;
    esac

    exit 1
}

api_call() {
    local method="$1"
    local path="$2"
    shift 2

    local body_file
    local url="${WORKSPACE_CORE_BASE_URL}${path}"

    body_file="$(mktemp "${TEMP_DIR}/response.XXXXXX")"

    if ! API_HTTP_CODE=$(curl -sS -b "$COOKIE_JAR" -c "$COOKIE_JAR" -o "$body_file" -w "%{http_code}" -X "$method" "$url" "$@"); then
        fail "Spring is unreachable at ${WORKSPACE_CORE_BASE_URL}. Make sure workspace-core is running."
    fi

    API_BODY_FILE="$body_file"
}

read_json() {
    local query="$1"
    jq -er "$query" "$API_BODY_FILE" 2>/dev/null \
        || fail "Expected JSON response matching jq query ${query}, but the response was missing that field or was not valid JSON"
}

read_json_optional() {
    local query="$1"
    jq -er "$query" "$API_BODY_FILE" 2>/dev/null || true
}

urlencode() {
    jq -nr --arg value "$1" '$value|@uri'
}

is_truthy() {
    local normalized
    normalized="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
    case "$normalized" in
        1|true|yes|on)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

derive_search_query() {
    local transcript_text
    transcript_text=$(jq -er '.[0].text // empty' "$API_BODY_FILE" 2>/dev/null || true)
    if [[ -z "${transcript_text// }" ]]; then
        echo ""
        return
    fi

    awk '
        {
            count = 0
            for (i = 1; i <= NF && count < 6; i++) {
                printf "%s%s", $i, (count < 5 && i < NF ? " " : "")
                count++
            }
        }
    ' <<<"$transcript_text"
}

ensure_workspace_core_ready() {
    print_step "Checking workspace-core health"
    api_call GET "/health"

    if [[ "$API_HTTP_CODE" != "200" ]]; then
        fail_api "workspace-core health check failed"
    fi

    echo "healthStatus: $(read_json '.status')"
    echo "healthService: $(read_json '.service')"
}

establish_authenticated_session() {
    if is_truthy "$SMOKE_USE_LEGACY_AUTH_FALLBACK"; then
        print_step "Establishing local/dev auth-session fallback"
        LEGACY_AUTH_BODY="$(jq -nc --arg userId "$SMOKE_LEGACY_USER_ID" '{userId: $userId}')"
        api_call POST "/api/auth/session" \
            -H "Content-Type: application/json" \
            --data "$LEGACY_AUTH_BODY"

        if [[ "$API_HTTP_CODE" != "200" ]]; then
            fail_api "Legacy auth-session setup failed"
        fi

        echo "authMode: legacy-auth-session"
        echo "currentUserId: $(read_json '.userId')"
        return
    fi

    print_step "Establishing authenticated product session"
    AUTH_REQUEST_BODY="$(jq -nc --arg email "$SMOKE_AUTH_EMAIL" --arg password "$SMOKE_AUTH_PASSWORD" '{email: $email, password: $password}')"

    api_call POST "/api/auth/register" \
        -H "Content-Type: application/json" \
        --data "$AUTH_REQUEST_BODY"

    if [[ "$API_HTTP_CODE" == "201" ]]; then
        echo "authMode: register"
    elif [[ "$API_HTTP_CODE" == "409" && "$(read_json '.code')" == "EMAIL_ALREADY_REGISTERED" ]]; then
        api_call POST "/api/auth/login" \
            -H "Content-Type: application/json" \
            --data "$AUTH_REQUEST_BODY"

        if [[ "$API_HTTP_CODE" != "200" ]]; then
            fail_api "Auth login failed after duplicate-register fallback"
        fi

        echo "authMode: login"
    else
        fail_api "Auth register failed"
    fi

    api_call GET "/api/me"
    if [[ "$API_HTTP_CODE" != "200" ]]; then
        fail_api "Authenticated current-user read failed"
    fi

    echo "authenticatedUserId: $(read_json '.id')"
    echo "authenticatedUserEmail: $(read_json '.email')"
}

if [[ $# -lt 1 ]]; then
    usage
    exit 1
fi

MEDIA_FILE_PATH="$1"
SMOKE_SEARCH_QUERY="${2:-}"
UPLOAD_TITLE="${3:-$(basename "$MEDIA_FILE_PATH")}"

[[ -f "$MEDIA_FILE_PATH" ]] || fail "Media file not found: $MEDIA_FILE_PATH"
[[ "$SMOKE_POLL_INTERVAL_SECONDS" =~ ^[0-9]+$ ]] || fail "SMOKE_POLL_INTERVAL_SECONDS must be an integer"
[[ "$SMOKE_POLL_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || fail "SMOKE_POLL_TIMEOUT_SECONDS must be an integer"
(( SMOKE_POLL_INTERVAL_SECONDS > 0 )) || fail "SMOKE_POLL_INTERVAL_SECONDS must be greater than 0"
(( SMOKE_POLL_TIMEOUT_SECONDS > 0 )) || fail "SMOKE_POLL_TIMEOUT_SECONDS must be greater than 0"

if is_truthy "$SMOKE_VERIFY_CONTEXT"; then
    [[ "$SMOKE_CONTEXT_WINDOW" =~ ^[0-9]+$ ]] || fail "SMOKE_CONTEXT_WINDOW must be an integer"
    (( SMOKE_CONTEXT_WINDOW > 0 )) || fail "SMOKE_CONTEXT_WINDOW must be greater than 0"
    (( SMOKE_CONTEXT_WINDOW <= 5 )) || fail "SMOKE_CONTEXT_WINDOW must be less than or equal to 5"
fi

require_command curl
require_command jq
initialize_auth_defaults

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/workspace-core-smoke.XXXXXX")"
trap cleanup EXIT
COOKIE_JAR="${TEMP_DIR}/cookies.txt"
touch "$COOKIE_JAR"

ensure_workspace_core_ready
establish_authenticated_session

if [[ -n "${SMOKE_WORKSPACE_NAME// }" ]]; then
    print_step "Creating a non-default workspace"
    WORKSPACE_CREATE_BODY="$(jq -nc --arg name "$SMOKE_WORKSPACE_NAME" '{name: $name}')"
    api_call POST "/api/workspaces" \
        -H "Content-Type: application/json" \
        --data "$WORKSPACE_CREATE_BODY"

    if [[ "$API_HTTP_CODE" != "201" ]]; then
        fail_api "Workspace creation failed"
    fi

    SMOKE_WORKSPACE_ID="$(read_json '.id')"
    CREATED_WORKSPACE_NAME="$(read_json '.name')"

    echo "workspaceId: ${SMOKE_WORKSPACE_ID}"
    echo "workspaceName: ${CREATED_WORKSPACE_NAME}"

    print_step "Reading the created workspace"
    api_call GET "/api/workspaces/${SMOKE_WORKSPACE_ID}"

    if [[ "$API_HTTP_CODE" != "200" ]]; then
        fail_api "Workspace read failed"
    fi

    READ_BACK_WORKSPACE_ID="$(read_json '.id')"
    [[ "$READ_BACK_WORKSPACE_ID" == "$SMOKE_WORKSPACE_ID" ]] || fail "Workspace read returned a different workspace ID"
fi

print_step "Uploading media through Spring"
UPLOAD_ARGS=(
    -F "file=@${MEDIA_FILE_PATH}"
    -F "title=${UPLOAD_TITLE}"
)

if [[ -n "${SMOKE_WORKSPACE_ID}" ]]; then
    UPLOAD_ARGS+=(-F "workspaceId=${SMOKE_WORKSPACE_ID}")
fi

api_call POST "/api/assets/upload" "${UPLOAD_ARGS[@]}"

if [[ "$API_HTTP_CODE" != "202" ]]; then
    fail_api "Upload failed"
fi

ASSET_ID="$(read_json '.assetId')"
PROCESSING_JOB_ID="$(read_json '.processingJobId')"
ASSET_STATUS="$(read_json '.assetStatus')"
UPLOAD_WORKSPACE_ID="$(read_json '.workspaceId')"

echo "assetId: ${ASSET_ID}"
echo "processingJobId: ${PROCESSING_JOB_ID}"
echo "assetStatus: ${ASSET_STATUS}"
echo "uploadWorkspaceId: ${UPLOAD_WORKSPACE_ID}"

if [[ -n "${SMOKE_WORKSPACE_ID}" && "$UPLOAD_WORKSPACE_ID" != "$SMOKE_WORKSPACE_ID" ]]; then
    fail "Upload response workspaceId did not match the created workspace"
fi

print_step "Listing assets in the resolved workspace"
ASSET_LIST_PATH="/api/assets"
if [[ -n "${SMOKE_WORKSPACE_ID}" ]]; then
    ASSET_LIST_PATH="${ASSET_LIST_PATH}?workspaceId=${SMOKE_WORKSPACE_ID}"
fi
api_call GET "$ASSET_LIST_PATH"

if [[ "$API_HTTP_CODE" != "200" ]]; then
    fail_api "Asset list failed"
fi

LISTED_ASSET_COUNT="$(jq --arg asset_id "$ASSET_ID" '[.items[]? | select(.assetId == $asset_id)] | length' "$API_BODY_FILE")"
echo "matchingListedAssets: ${LISTED_ASSET_COUNT}"

if (( LISTED_ASSET_COUNT == 0 )); then
    fail "Uploaded asset was not returned by the workspace-scoped asset list"
fi

print_step "Polling asset status until terminal or timeout"
START_TIME=$SECONDS
PROCESSING_JOB_STATUS=""

while true; do
    api_call GET "/api/assets/${ASSET_ID}/status"

    if [[ "$API_HTTP_CODE" != "200" ]]; then
        fail_api "Status polling failed"
    fi

    PROCESSING_JOB_STATUS="$(read_json '.processingJobStatus')"
    ASSET_STATUS="$(read_json '.assetStatus')"
    ELAPSED_SECONDS=$((SECONDS - START_TIME))

    echo "progress: elapsed=${ELAPSED_SECONDS}s processingJobStatus=${PROCESSING_JOB_STATUS} assetStatus=${ASSET_STATUS}"

    if [[ "$PROCESSING_JOB_STATUS" == "SUCCEEDED" ]]; then
        break
    fi

    if [[ "$PROCESSING_JOB_STATUS" == "FAILED" ]]; then
        fail "Processing reached terminal failure for asset ${ASSET_ID}"
    fi

    if (( ELAPSED_SECONDS >= SMOKE_POLL_TIMEOUT_SECONDS )); then
        fail "Processing never reached a terminal state within ${SMOKE_POLL_TIMEOUT_SECONDS}s"
    fi

    sleep "$SMOKE_POLL_INTERVAL_SECONDS"
done

print_step "Fetching transcript through Spring"
api_call GET "/api/assets/${ASSET_ID}/transcript"

if [[ "$API_HTTP_CODE" != "200" ]]; then
    if [[ "$API_HTTP_CODE" == "409" ]]; then
        fail_api "Transcript is not usable for this asset"
    fi
    fail_api "Transcript fetch failed"
fi

TRANSCRIPT_ROW_COUNT="$(jq 'length' "$API_BODY_FILE")"
echo "transcriptRowCount: ${TRANSCRIPT_ROW_COUNT}"

if (( TRANSCRIPT_ROW_COUNT == 0 )); then
    fail "Transcript returned zero rows"
fi

if [[ -z "${SMOKE_SEARCH_QUERY// }" ]]; then
    SMOKE_SEARCH_QUERY="$(derive_search_query)"
fi

if [[ -z "${SMOKE_SEARCH_QUERY// }" ]]; then
    SMOKE_SEARCH_QUERY="${UPLOAD_TITLE}"
fi

print_step "Indexing the asset"
api_call POST "/api/assets/${ASSET_ID}/index"

if [[ "$API_HTTP_CODE" != "200" ]]; then
    fail_api "Indexing failed"
fi

INDEXED_DOCUMENT_COUNT="$(read_json '.indexedDocumentCount')"
ASSET_STATUS="$(read_json '.assetStatus')"

echo "indexedDocumentCount: ${INDEXED_DOCUMENT_COUNT}"
echo "assetStatusAfterIndex: ${ASSET_STATUS}"

if [[ "$ASSET_STATUS" != "SEARCHABLE" ]]; then
    fail "Indexing did not leave the asset in SEARCHABLE state"
fi

print_step "Running product search"
ENCODED_SEARCH_QUERY="$(urlencode "$SMOKE_SEARCH_QUERY")"
SEARCH_PATH="/api/search?q=${ENCODED_SEARCH_QUERY}&assetId=${ASSET_ID}"
if [[ -n "${SMOKE_WORKSPACE_ID}" ]]; then
    SEARCH_PATH="${SEARCH_PATH}&workspaceId=${SMOKE_WORKSPACE_ID}"
fi
api_call GET "$SEARCH_PATH"

if [[ "$API_HTTP_CODE" != "200" ]]; then
    fail_api "Search failed"
fi

SEARCH_RESULT_COUNT="$(read_json '.resultCount')"
SEARCH_WORKSPACE_ID="$(read_json '.workspaceIdFilter')"
echo "searchQuery: ${SMOKE_SEARCH_QUERY}"
echo "searchResultCount: ${SEARCH_RESULT_COUNT}"
echo "searchWorkspaceId: ${SEARCH_WORKSPACE_ID}"

if (( SEARCH_RESULT_COUNT == 0 )); then
    fail "Search returned zero results for the indexed asset"
fi

if [[ -n "${SMOKE_WORKSPACE_ID}" && "$SEARCH_WORKSPACE_ID" != "$SMOKE_WORKSPACE_ID" ]]; then
    fail "Search workspaceIdFilter did not match the created workspace"
fi

if is_truthy "$SMOKE_VERIFY_CONTEXT"; then
    print_step "Fetching transcript context for the top search hit"
    SEARCH_HIT_ASSET_ID="$(read_json '.results[0].assetId')"
    SEARCH_HIT_SEGMENT_INDEX="$(read_json '.results[0].segmentIndex')"
    SEARCH_HIT_TRANSCRIPT_ROW_ID="$(read_json_optional '.results[0].transcriptRowId // empty')"

    if [[ -z "${SEARCH_HIT_TRANSCRIPT_ROW_ID// }" ]]; then
        if [[ -z "${SEARCH_HIT_SEGMENT_INDEX// }" || "$SEARCH_HIT_SEGMENT_INDEX" == "null" ]]; then
            fail "Top search hit did not include transcriptRowId or segmentIndex for context lookup"
        fi
        SEARCH_HIT_TRANSCRIPT_ROW_ID="segment-${SEARCH_HIT_SEGMENT_INDEX}"
    fi

    ENCODED_TRANSCRIPT_ROW_ID="$(urlencode "$SEARCH_HIT_TRANSCRIPT_ROW_ID")"
    api_call GET "/api/assets/${SEARCH_HIT_ASSET_ID}/transcript/context?transcriptRowId=${ENCODED_TRANSCRIPT_ROW_ID}&window=${SMOKE_CONTEXT_WINDOW}"

    if [[ "$API_HTTP_CODE" != "200" ]]; then
        fail_api "Transcript context fetch failed"
    fi

    CONTEXT_ROW_COUNT="$(jq '.rows | length' "$API_BODY_FILE")"
    echo "contextAssetId: $(read_json '.assetId')"
    echo "contextTranscriptRowId: $(read_json '.transcriptRowId')"
    echo "contextHitSegmentIndex: $(read_json '.hitSegmentIndex')"
    echo "contextWindow: $(read_json '.window')"
    echo "contextRowCount: ${CONTEXT_ROW_COUNT}"
    echo "contextRows:"
    jq -r '.rows[] | "  [\(.segmentIndex)] \((.text // "") | gsub("[\\r\\n]+"; " ") | if length > 100 then .[0:100] + "..." else . end)"' "$API_BODY_FILE"
fi

echo
echo "Smoke flow completed successfully."
