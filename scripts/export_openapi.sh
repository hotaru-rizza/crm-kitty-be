#!/usr/bin/env bash
set -euo pipefail

# Export OpenAPI spec and Postman collection from the running test context.
#
# Usage:
#   ./scripts/export_openapi.sh
#
# Outputs:
#   docs/openapi.yaml
#   docs/postman/InkFlow_API.postman_collection.json (gitignored, optional for Postman)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Generating OpenAPI spec (H2 test context)..."
./mvnw -q test -Dtest=OpenApiDocumentGenerationTest -Dgenerate.openapi=true

if [[ ! -f docs/openapi.yaml ]]; then
  echo "ERROR: docs/openapi.yaml was not created" >&2
  exit 1
fi

LINES="$(wc -l < docs/openapi.yaml | tr -d ' ')"
echo "Written docs/openapi.yaml ($LINES lines)"

mkdir -p docs/postman

if command -v npx >/dev/null 2>&1; then
  echo "Converting to Postman collection..."
  npx --yes openapi-to-postmanv2 \
    -s docs/openapi.yaml \
    -o docs/postman/InkFlow_API.postman_collection.json \
    -p
  echo "Written docs/postman/InkFlow_API.postman_collection.json"
else
  echo "WARN: npx not found — skip Postman export. Import docs/openapi.yaml manually."
fi

echo "Done."
