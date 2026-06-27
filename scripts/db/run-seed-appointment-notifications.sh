#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
SQL_FILE="${ROOT_DIR}/scripts/db/seed-appointment-notifications.sql"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing ${ENV_FILE}. Set SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD." >&2
  exit 1
fi

cd "$ROOT_DIR"

DATABASE_URL="$(python3 - <<'PY'
import re
import urllib.parse
from pathlib import Path

env = {}
for line in Path(".env").read_text().splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, _, value = line.partition("=")
    env[key.strip()] = value.strip().strip('"').strip("'")

jdbc = env.get("SPRING_DATASOURCE_URL", "")
user = env.get("DB_USERNAME", "")
password = env.get("DB_PASSWORD", "")
if not jdbc:
    raise SystemExit("SPRING_DATASOURCE_URL is missing in .env")

match = re.match(r"jdbc:postgresql://([^/]+)/([^?]+)(\?(.*))?", jdbc)
if not match:
    raise SystemExit(f"Unsupported JDBC URL format: {jdbc}")

host_port, database, query = match.group(1), match.group(2), match.group(4) or ""
params = urllib.parse.parse_qs(query, keep_blank_values=True)
allowed = {key: values[0] for key, values in params.items() if key in ("sslmode", "options")}
query_string = f"?{urllib.parse.urlencode(allowed)}" if allowed else ""

print(
    f"postgresql://{urllib.parse.quote(user, safe='')}:"
    f"{urllib.parse.quote(password, safe='')}@{host_port}/{database}{query_string}"
)
PY
)"

psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$SQL_FILE"
echo "Appointment notification demo data applied."
