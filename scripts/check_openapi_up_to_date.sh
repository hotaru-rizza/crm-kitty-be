#!/usr/bin/env bash
set -euo pipefail

# Fail if docs/openapi.yaml is out of sync with current controllers/DTOs.
# Use in CI or before PR that touches API.
#
# Fix: ./scripts/export_openapi.sh && git add docs/

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f docs/openapi.yaml ]]; then
  echo "ERROR: docs/openapi.yaml missing. Run ./scripts/export_openapi.sh" >&2
  exit 1
fi

TMP="$(mktemp)"
cp docs/openapi.yaml "$TMP"

./mvnw -q test -Dtest=OpenApiDocumentGenerationTest -Dgenerate.openapi=true

if diff -q "$TMP" docs/openapi.yaml >/dev/null 2>&1; then
  echo "OK: docs/openapi.yaml is up to date"
  rm -f "$TMP"
  exit 0
fi

echo "ERROR: docs/openapi.yaml is stale. Run:" >&2
echo "  ./scripts/export_openapi.sh" >&2
echo "  git add docs/openapi.yaml" >&2
rm -f "$TMP"
exit 1
