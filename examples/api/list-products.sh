#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
SEARCH="${1:-}"

curl -sS --fail-with-body -G "$PORTAL_URL/api/products" --data-urlencode "search=$SEARCH" | jq .
