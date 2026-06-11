#!/usr/bin/env bash
set -euo pipefail

# Export current PostgreSQL schema as Flyway baseline.
#
# Usage (any of):
#   DATABASE_URL=postgres://user:pass@host:5432/db ./scripts/export_flyway_baseline.sh
#   ./scripts/export_flyway_baseline.sh   # reads .env in repo root

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="${ROOT_DIR}/src/main/resources/db/migration/V1__baseline.sql"
ENV_FILE="${ROOT_DIR}/.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

if [[ -z "${DATABASE_URL:-}" && -n "${SPRING_DATASOURCE_URL:-}" ]]; then
  DATABASE_URL="$(python3 - <<'PY'
import os
import re
import urllib.parse

jdbc = os.environ["SPRING_DATASOURCE_URL"]
user = os.environ.get("DB_USERNAME", "")
password = os.environ.get("DB_PASSWORD", "")

match = re.match(r"jdbc:postgresql://([^/]+)/([^?]+)(\?.*)?", jdbc)
if not match:
    raise SystemExit(f"Unsupported JDBC URL format: {jdbc}")

host_port, database, query = match.group(1), match.group(2), match.group(3) or ""
user = urllib.parse.quote(user, safe="")
password = urllib.parse.quote(password, safe="")
print(f"postgresql://{user}:{password}@{host_port}/{database}{query}")
PY
)"
fi

DATABASE_URL="${DATABASE_URL:?Set DATABASE_URL or SPRING_DATASOURCE_URL + DB_USERNAME + DB_PASSWORD in .env}"

mkdir -p "$(dirname "$OUTPUT")"
pg_dump "$DATABASE_URL" \
  --schema-only \
  --no-owner \
  --no-privileges \
  --schema=public \
  | sed '/^\\restrict /d; /^\\unrestrict /d' \
  > "$OUTPUT"
echo "Baseline written to $OUTPUT ($(wc -l < "$OUTPUT" | tr -d ' ') lines)"
