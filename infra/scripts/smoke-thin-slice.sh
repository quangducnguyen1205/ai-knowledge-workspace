#!/usr/bin/env bash

set -euo pipefail

WORKSPACE_CORE_BASE_URL="${WORKSPACE_CORE_BASE_URL:-http://localhost:8081}"
SMOKE_POLL_INTERVAL_SECONDS="${SMOKE_POLL_INTERVAL_SECONDS:-3}"
SMOKE_POLL_TIMEOUT_SECONDS="${SMOKE_POLL_TIMEOUT_SECONDS:-180}"

API_HTTP_CODE=""
API_BODY_FILE=""
TEMP_DIR=""

usage() {
    cat <<'EOF'
Usage:
  ./infra/scripts/smoke-thin-slice.sh /path/to/media-file [search-query] [title]

Environment variables:
  WORKSPACE_CORE_BASE_URL       Default: http://localhost:8081
  SMOKE_POLL_INTERVAL_SECONDS   Default: 3
  SMOKE_POLL_TIMEOUT_SECONDS    Default: 180

Notes:
  - Repo A, PostgreSQL, Elasticsearch, and workspace-core must already be running.
  - If search-query is omitted, the script derives a simple query from the first transcript row.
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

fail_api() {
    local message="$1"
    echo
    echo "ERROR: $message" >&2
    echo "HTTP status: $API_HTTP_CODE" >&2

    if [[ -s "$API_BODY_FILE" ]]; then
        echo "Response body:" >&2
        pretty_print_body "$API_BODY_FILE" >&2
    fi

    exit 1
}

api_call() {
    local method="$1"
    local path="$2"
    shift 2

    local body_file="${TEMP_DIR}/response-$(date +%s%N).json"
    local url="${WORKSPACE_CORE_BASE_URL}${path}"

    if ! API_HTTP_CODE=$(curl -sS -o "$body_file" -w "%{http_code}" -X "$method" "$url" "$@"); then
        fail "Spring is unreachable at ${WORKSPACE_CORE_BASE_URL}. Make sure workspace-core is running."
    fi

    API_BODY_FILE="$body_file"
}

read_json() {
    local query="$1"
    jq -r "$query" "$API_BODY_FILE"
}

urlencode() {
    jq -nr --arg value "$1" '$value|@uri'
}

derive_search_query() {
    local transcript_text
    transcript_text=$(jq -r '.[0].text // empty' "$API_BODY_FILE")
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

require_command curl
require_command jq

TEMP_DIR="$(mktemp -d)"
trap cleanup EXIT

print_step "Uploading media through Spring"
api_call POST "/api/assets/upload" \
    -F "file=@${MEDIA_FILE_PATH}" \
    -F "title=${UPLOAD_TITLE}"

if [[ "$API_HTTP_CODE" != "202" ]]; then
    fail_api "Upload failed"
fi

ASSET_ID="$(read_json '.assetId')"
PROCESSING_JOB_ID="$(read_json '.processingJobId')"
ASSET_STATUS="$(read_json '.assetStatus')"

echo "assetId: ${ASSET_ID}"
echo "processingJobId: ${PROCESSING_JOB_ID}"
echo "assetStatus: ${ASSET_STATUS}"

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
api_call GET "/api/search?q=${ENCODED_SEARCH_QUERY}&assetId=${ASSET_ID}"

if [[ "$API_HTTP_CODE" != "200" ]]; then
    fail_api "Search failed"
fi

SEARCH_RESULT_COUNT="$(read_json '.resultCount')"
echo "searchQuery: ${SMOKE_SEARCH_QUERY}"
echo "searchResultCount: ${SEARCH_RESULT_COUNT}"

if (( SEARCH_RESULT_COUNT == 0 )); then
    fail "Search returned zero results for the indexed asset"
fi

echo
echo "Smoke flow completed successfully."
