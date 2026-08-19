ALTER TABLE async_job
    ADD COLUMN active_slot TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN status IN ('PENDING', 'RUNNING') THEN 1 ELSE NULL END
        ) STORED,
    ADD CONSTRAINT uk_async_job_active_target
        UNIQUE (organization_id, job_type, target_type, target_id, active_slot);
