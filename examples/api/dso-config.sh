#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
KEY="${1:?usage: dso-config.sh PIPELINE_KEY}"

echo "== JSON, as the library reads it"
curl -sS --fail-with-body "$PORTAL_URL/api/dso/config/$KEY?format=json" | jq .

echo "== YAML, the config.yaml layout"
curl -sS --fail-with-body "$PORTAL_URL/api/dso/config/$KEY"
