# Email templates (INKAT)

Single place for all email-related assets in the backend.

## Layout (Resend / CRM emails)

Transactional and marketing emails sent by the backend are rendered with:

- `EmailLayout.java` — HTML shell (dark INKAT style, glow, logo, CTA)
- `TemplateDefaults.java` — default subject + plain-text body per `TemplateKey`
- `BuiltInTemplateCatalog.java` — built-in templates seeded into `email_template`

Studio overrides for configurable templates live in `email_template_override`.

## Supabase Auth templates

Copy-paste HTML from `templates/supabase-auth/` into **Supabase → Authentication → Email Templates**:

| File | Supabase template |
|------|-------------------|
| `confirm-signup.html` | Confirm signup |
| `reset-password.html` | Reset password |
| `password-changed.html` | Password changed |
| `subjects.txt` | Subject lines |

These are **not** sent by the Java backend — Supabase Auth sends them directly.

Logo in auth HTML uses inline base64 (same asset as `assets/inkat-logo-52.base64`).
