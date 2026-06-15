-- BIRTHDAY / WINBACK use system schedulers; studios can edit/disable but not delete the built-in row.
UPDATE email_template
SET deletable = FALSE
WHERE builtin_key IN ('BIRTHDAY', 'WINBACK');
