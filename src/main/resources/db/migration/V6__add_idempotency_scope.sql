ALTER TABLE idempotency_record
    ADD COLUMN scope VARCHAR(64) NULL AFTER organization_id;

UPDATE idempotency_record
SET scope = CONCAT('ORG:', organization_id)
WHERE scope IS NULL;

ALTER TABLE idempotency_record
    ADD INDEX idx_idempotency_org (organization_id),
    DROP INDEX uk_idempotency_user_key,
    MODIFY organization_id CHAR(26) NULL,
    MODIFY scope VARCHAR(64) NOT NULL,
    ADD CONSTRAINT uk_idempotency_scope_user_key
        UNIQUE (scope, cas_id, idempotency_key);
