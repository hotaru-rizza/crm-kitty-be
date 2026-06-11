# Entity ownership conventions

Where JPA entities live in `crm-kitty-be` and when to add new ones.

## Rules

| Location | Use for | Examples |
|----------|---------|----------|
| `domain/entity/` | Core multi-tenant CRM domain shared across modules | `Appointment`, `Client`, `Staff`, `Tenant`, `Transaction` |
| `module/catalog/entity/` | Tattoo catalog / portfolio (global feed, embeddings) | `Tattoo`, `TattooStyle`, `TattooStatus` |
| `module/consumer/entity/` | B2C app users and AI history | `ConsumerUser`, `AiGeneration` |
| `module/notification/entity/` | Push / in-app notifications | `Notification`, `DeviceToken` |

## Repositories

- Core CRM: `domain/repository/`
- Module-specific: `module/{name}/repository/` (e.g. catalog, consumer)

## Do not

- Add new entities to module root packages (flat layout).
- Split the same aggregate across `domain/` and a module without a clear boundary.
- Move existing `domain/entity` types into modules in bulk — only migrate when a module is fully isolated.

## New entity checklist

1. Is it tenant-scoped CRM core? → `domain/entity/`
2. Is it owned by one feature module only? → `module/{module}/entity/`
3. Repository next to consumers or in `domain/repository/` following existing pattern for that aggregate.
