#!/usr/bin/env bash
set -euo pipefail

# The Library defaults must allow the databases of both products (dev-db.bbh.com, rd-db.bbh.com and qc-db.bbh.com)
# and name the Nexus repositories of the database packages (Admin, Library defaults, Liquibase database changes),
# or the portal refuses the JDBC URLs and the Full pipelines.
PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
DEPARTMENT="${1:-Custody}"
FOLDER="$(dirname "$0")"

DEPARTMENT_ID=$(curl -sS --fail-with-body "$PORTAL_URL/api/departments" \
  | jq -e --arg name "$DEPARTMENT" '.[] | select(.name == $name) | .id')

for BODY in "$FOLDER/new-database-product.json" "$FOLDER/new-mixed-product.json"; do
  echo "Creating $(jq -r .code "$BODY") in $DEPARTMENT (department $DEPARTMENT_ID)"
  jq --argjson id "$DEPARTMENT_ID" '.departmentId = $id' "$BODY" \
    | curl -sS --fail-with-body -X POST "$PORTAL_URL/api/products?pipelineType=FULL" \
        -H 'Content-Type: application/json' --data-binary @- \
    | jq 'if .detail then . else {id, code, name, departmentId, version, services: [.services[] | {id, name}]} end'
done
