-- V8: Track the last successful run of background schedulers.
-- Lets jobs (e.g. the reminder scheduler) rebuild their processing window after downtime
-- instead of silently dropping everything that became due while the app was offline.

CREATE TABLE scheduler_run (
    job_key     varchar(64) PRIMARY KEY,
    last_run_at timestamptz NOT NULL
);
