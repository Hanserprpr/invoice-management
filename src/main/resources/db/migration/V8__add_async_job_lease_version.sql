ALTER TABLE async_job
    ADD COLUMN lease_version BIGINT UNSIGNED NOT NULL DEFAULT 0
        AFTER max_attempts;
