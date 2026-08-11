ALTER TABLE async_job
    ADD COLUMN next_attempt_at DATETIME(3) NULL AFTER error_message,
    ADD INDEX idx_job_retry (status, job_type, next_attempt_at, created_at);
