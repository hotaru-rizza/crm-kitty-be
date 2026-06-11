# Postman (local)

Generated output lives in **`docs/postman/`** (gitignored). Do not commit that folder.

## Setup

1. Import spec: Postman → Import → `docs/openapi.yaml`

   Or generate collection locally:
   ```bash
   ./scripts/export_openapi.sh
   ```
   Writes `docs/postman/InkFlow_API.postman_collection.json`.

2. Import environment: [staging.postman_environment.example.json](staging.postman_environment.example.json)  
   Copy and set `base_url`, `crm_token`, `consumer_token`.

See [docs/MOBILE_API.md](../../docs/MOBILE_API.md).
